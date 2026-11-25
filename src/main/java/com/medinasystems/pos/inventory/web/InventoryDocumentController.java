package com.medinasystems.pos.inventory.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.inventory.application.InventoryDocumentData;
import com.medinasystems.pos.inventory.application.InventoryDocumentService;
import com.medinasystems.pos.inventory.application.InventoryDocumentSummaryView;
import com.medinasystems.pos.inventory.application.InventoryDocumentView;
import com.medinasystems.pos.inventory.application.InventoryQueryService;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;
import com.medinasystems.pos.shared.SerialNumbers;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Inventory documents: entries, exits and counts. Both nodes post documents; a store posts for its own branch and uploads
 * them to the central node. The central node names the branch of each document.
 */
@RestController
@RequestMapping("/api/v1/inventory/documents")
public class InventoryDocumentController {

    private final InventoryDocumentService documents;
    private final InventoryQueryService queries;

    public InventoryDocumentController(InventoryDocumentService documents, InventoryQueryService queries) {
        this.documents = documents;
        this.queries = queries;
    }

    /**
     * Posts a document. Entries and exits read {@code quantity}; adjustments read {@code countedQuantity}. An exit and an
     * adjustment need a reason. A key already used returns the document it created.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDocumentView post(@Valid @RequestBody DocumentRequest request) {
        return documents.post(request.toData());
    }

    /** {@code search} matches part of the document number. */
    @GetMapping
    public PageResponse<InventoryDocumentSummaryView> list(@RequestParam(required = false) Long branchId,
                                                           @RequestParam(required = false) InventoryDocumentType type,
                                                           @RequestParam(required = false) String search,
                                                           @RequestParam(required = false) Instant from,
                                                           @RequestParam(required = false) Instant to,
                                                           @PageableDefault(sort = {"postedAt", "id"},
                                                                   direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(queries.documents(branchId, type, search, from, to, pageable));
    }

    @GetMapping("/{id}")
    public InventoryDocumentView get(@PathVariable long id) {
        return queries.document(id);
    }

    /** The branch is optional on a store (its own) and required on the central node. */
    public record DocumentRequest(@NotNull InventoryDocumentType type,
                                  Long branchId,
                                  @Size(max = 200) String reason,
                                  @Size(max = 500) String notes,
                                  @Size(max = 64) String idempotencyKey,
                                  @NotEmpty @Size(max = 500) List<@Valid LineRequest> lines) {

        InventoryDocumentData toData() {
            return new InventoryDocumentData(type, branchId, reason, notes, idempotencyKey,
                    lines.stream().map(LineRequest::toData).toList());
        }
    }

    public record LineRequest(@NotNull Long productId,
                              @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                              @Digits(integer = 9, fraction = 3) BigDecimal countedQuantity,
                              @Digits(integer = 10, fraction = 4) BigDecimal unitCost,
                              List<@NotBlank @Size(max = SerialNumbers.MAX_LENGTH) String> serialNumbers) {

        InventoryDocumentData.LineData toData() {
            return new InventoryDocumentData.LineData(productId, quantity, countedQuantity, unitCost, serialNumbers);
        }
    }
}
