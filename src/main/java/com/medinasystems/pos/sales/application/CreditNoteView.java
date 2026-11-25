package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.CreditNoteStatus;

/** A credit note with the customer's name and the receipt number of the return that issued it. */
public record CreditNoteView(Long id, String number, Long customerId, String customerName, Long returnSaleId,
                             String returnReceiptNumber, Long branchId, BigDecimal amount, BigDecimal balance,
                             CreditNoteStatus status, Instant issuedAt) {

    public static CreditNoteView from(CreditNote note, String customerName, String returnReceiptNumber) {
        return new CreditNoteView(note.getId(), note.getNumber(), note.getCustomerId(), customerName,
                note.getReturnSaleId(), returnReceiptNumber, note.getBranchId(), note.getAmount(), note.getBalance(),
                note.getStatus(), note.getIssuedAt());
    }
}
