package com.medinasystems.pos.sales.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sales.application.ReleaseClaimsResult;
import com.medinasystems.pos.sales.application.ReturnClaimService;
import com.medinasystems.pos.sales.application.ReturnableView;
import com.medinasystems.pos.sales.domain.ReturnLineRequest;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.sync.StoreCaller;

/**
 * Return calls that a store makes to the central node for sales of other branches and for the claims of its own returns.
 * The store token authenticates them (the sync filter). Only the central node answers them.
 */
@RestController
@RequestMapping("/api/v1/sync/sales")
@CentralOnly
public class ReturnClaimSyncController {

    private final ReturnClaimService claims;

    public ReturnClaimSyncController(ReturnClaimService claims) {
        this.claims = claims;
    }

    /** The returnable view of a paid sale, found by its receipt number. 404 when there is none. */
    @GetMapping("/returnable")
    public ReturnableView byReceipt(@RequestParam("receiptNumber") @NotBlank @Size(max = 30) String receiptNumber) {
        return claims.returnableByReceipt(receiptNumber.trim());
    }

    /** The returnable view of a paid sale, by id. 404 when there is none. */
    @GetMapping("/{id}/returnable")
    public ReturnableView byId(@PathVariable("id") long id) {
        return claims.returnable(id);
    }

    /** Holds the quantities of a return. Asking again with the same claim key answers the current view. */
    @PostMapping("/{id}/return-claims")
    public ReturnableView reserve(@PathVariable("id") long id, @Valid @RequestBody ReserveRequest request,
                                  @RequestAttribute(StoreCaller.NODE_ATTRIBUTE) long storeNodeId) {
        List<ReturnLineRequest> lines = request.lines().stream()
                .map(line -> new ReturnLineRequest(line.saleLineId(), line.quantity()))
                .toList();
        return claims.reserve(id, request.claimKey(), lines, storeNodeId);
    }

    /** Gives back the reserved quantities of a return that failed. A key with nothing reserved is a no-op. */
    @PostMapping("/return-claims/{claimKey}/release")
    public ReleaseClaimsResult release(@PathVariable("claimKey") String claimKey,
                                       @RequestAttribute(StoreCaller.NODE_ATTRIBUTE) long storeNodeId) {
        return claims.release(claimKey, storeNodeId);
    }

    public record ReserveRequest(@NotBlank @Size(max = 100) String claimKey,
                                 @NotEmpty @Valid List<ClaimLine> lines) {
    }

    public record ClaimLine(@NotNull Long saleLineId, @NotNull @Positive BigDecimal quantity) {
    }
}
