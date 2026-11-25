package com.medinasystems.pos.sales.web;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.shared.BusinessConflictException;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.sales.application.CreditNoteBalanceView;
import com.medinasystems.pos.sales.application.CreditNoteRedemptionService;
import com.medinasystems.pos.sales.application.CreditNoteRedemptionService.ReleaseOutcome;
import com.medinasystems.pos.sales.application.RedemptionResult;
import com.medinasystems.pos.sales.application.ReleaseResult;
import com.medinasystems.pos.sync.StoreCaller;

/**
 * Credit note calls that a store makes to the central node while it charges a sale. The store token authenticates
 * them (the sync filter). They change balances, so only the central node answers them.
 */
@RestController
@RequestMapping("/api/v1/sync/credit-notes")
@CentralOnly
public class CreditNoteSyncController {

    private final CreditNoteRedemptionService redemptions;

    public CreditNoteSyncController(CreditNoteRedemptionService redemptions) {
        this.redemptions = redemptions;
    }

    /** The balance of a note, for the cashier's screen and for the store's checks. */
    @GetMapping("/{number}")
    public CreditNoteBalanceView balance(@PathVariable("number") String number) {
        return redemptions.balance(number);
    }

    /** Takes the amount of one payment from the note. Answers the same redemption when the key is asked again. */
    @PostMapping("/{number}/redemptions")
    public RedemptionResult redeem(@PathVariable("number") String number,
                                   @Valid @RequestBody RedeemRequest request,
                                   @RequestAttribute(StoreCaller.NODE_ATTRIBUTE) long storeNodeId) {
        return redemptions.redeem(number, request.redemptionKey(), request.amount(), storeNodeId);
    }

    /** Gives back the amount of a payment that failed. Releasing an unknown or released key is a no-op. */
    @PostMapping("/redemptions/{redemptionKey}/release")
    public ReleaseResult release(@PathVariable("redemptionKey") String redemptionKey,
                                 @RequestAttribute(StoreCaller.NODE_ATTRIBUTE) long storeNodeId) {
        ReleaseOutcome outcome = redemptions.release(redemptionKey, storeNodeId);
        if (outcome == ReleaseOutcome.CONFIRMED) {
            throw new BusinessConflictException("CREDIT_NOTE_REDEMPTION_CONFIRMED",
                    "The sale that used this redemption is already confirmed");
        }
        return new ReleaseResult(redemptionKey, outcome.name());
    }

    public record RedeemRequest(@NotBlank @Size(max = 100) String redemptionKey, @NotNull @Positive BigDecimal amount) {
    }
}
