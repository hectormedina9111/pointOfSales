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

import com.medinasystems.pos.inventory.application.InventoryTransferQueryService;
import com.medinasystems.pos.inventory.application.InventoryTransferService;
import com.medinasystems.pos.inventory.application.ReceiptData;
import com.medinasystems.pos.inventory.application.TransferBranchView;
import com.medinasystems.pos.inventory.application.TransferData;
import com.medinasystems.pos.inventory.application.TransferSummaryView;
import com.medinasystems.pos.inventory.application.TransferView;
import com.medinasystems.pos.inventory.domain.TransferDirection;
import com.medinasystems.pos.inventory.domain.TransferStatus;
import com.medinasystems.pos.shared.SerialNumbers;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Transfers between branches. The origin branch sends a transfer and the destination branch receives it; a store does both for
 * its own branch, and the central node for the branches that no store serves.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryTransferController {

    private final InventoryTransferService transferService;
    private final InventoryTransferQueryService queries;

    public InventoryTransferController(InventoryTransferService transferService, InventoryTransferQueryService queries) {
        this.transferService = transferService;
        this.queries = queries;
    }

    /** Sends a transfer. The origin is required on the central node and optional on a store. A key already used returns it. */
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferView send(@Valid @RequestBody SendRequest request) {
        return transferService.send(request.toData());
    }

    /** Records the receipt of a transfer by its destination branch: every line, the quantity received and its serial numbers. */
    @PostMapping("/transfers/{id}/receipt")
    public TransferView receive(@PathVariable long id, @Valid @RequestBody ReceiptRequest request) {
        return transferService.receive(id, request.toData());
    }

    /**
     * {@code direction} with {@code branchId} lists what the branch sent (OUTGOING) or receives (INCOMING); without a direction,
     * both. Without a branch, the central node lists every transfer and the direction is ignored. {@code search} matches part of
     * the number.
     */
    @GetMapping("/transfers")
    public PageResponse<TransferSummaryView> list(@RequestParam(required = false) TransferDirection direction,
                                                  @RequestParam(required = false) Long branchId,
                                                  @RequestParam(required = false) TransferStatus status,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Instant from,
                                                  @RequestParam(required = false) Instant to,
                                                  @PageableDefault(sort = {"sentAt", "id"},
                                                          direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(queries.transfers(branchId, direction, status, search, from, to, pageable));
    }

    @GetMapping("/transfers/{id}")
    public TransferView get(@PathVariable long id) {
        return queries.transfer(id);
    }

    /** The active branches, for the origin and destination choices, and whether this node operates each one. */
    @GetMapping("/transfer-branches")
    public List<TransferBranchView> branches() {
        return queries.branches();
    }

    /** The origin is optional on a store (its own branch) and required on the central node. */
    public record SendRequest(Long originBranchId,
                              @NotNull Long destinationBranchId,
                              @Size(max = 500) String notes,
                              @Size(max = 64) String idempotencyKey,
                              @NotEmpty @Size(max = 500) List<@Valid SendLineRequest> lines) {

        TransferData toData() {
            return new TransferData(originBranchId, destinationBranchId, notes, idempotencyKey,
                    lines.stream().map(SendLineRequest::toData).toList());
        }
    }

    public record SendLineRequest(@NotNull Long productId,
                                  @NotNull @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                  List<@NotBlank @Size(max = SerialNumbers.MAX_LENGTH) String> serialNumbers) {

        TransferData.LineData toData() {
            return new TransferData.LineData(productId, quantity, serialNumbers == null ? List.of() : serialNumbers);
        }
    }

    public record ReceiptRequest(@Size(max = 500) String notes,
                                 @Size(max = 64) String idempotencyKey,
                                 @NotEmpty @Size(max = 500) List<@Valid ReceiptLineRequest> lines) {

        ReceiptData toData() {
            return new ReceiptData(notes, idempotencyKey, lines.stream().map(ReceiptLineRequest::toData).toList());
        }
    }

    public record ReceiptLineRequest(@NotNull Long lineId,
                                     @NotNull @Digits(integer = 9, fraction = 3) BigDecimal receivedQuantity,
                                     List<@NotBlank @Size(max = SerialNumbers.MAX_LENGTH) String> serialNumbers) {

        ReceiptData.LineData toData() {
            return new ReceiptData.LineData(lineId, receivedQuantity, serialNumbers == null ? List.of() : serialNumbers);
        }
    }
}
