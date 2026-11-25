package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.CreditNoteStatus;

/** A credit note as the central node sees it now: its balance and status. Stores read it to pay with it. */
public record CreditNoteBalanceView(long id, String number, long customerId, String customerName, BigDecimal amount,
                                    BigDecimal balance, CreditNoteStatus status) {

    public static CreditNoteBalanceView of(CreditNote note, String customerName) {
        return new CreditNoteBalanceView(note.getId(), note.getNumber(), note.getCustomerId(), customerName,
                note.getAmount(), note.getBalance(), note.getStatus());
    }
}
