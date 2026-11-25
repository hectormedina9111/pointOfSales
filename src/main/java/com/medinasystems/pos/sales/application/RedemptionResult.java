package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.CreditNoteRedemption;

/** The answer to a redemption: its id, the note and the balance that is left on it. */
public record RedemptionResult(long redemptionId, long creditNoteId, BigDecimal balance) {

    static RedemptionResult of(CreditNoteRedemption redemption, CreditNote note) {
        return new RedemptionResult(redemption.getId(), note.getId(), note.getBalance());
    }
}
