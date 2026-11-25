package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.sales.domain.CreditNoteRedemption;
import com.medinasystems.pos.sales.domain.CreditNoteRedemptionStatus;

/** One redemption of a credit note, as listed on the central node. */
public record CreditNoteRedemptionView(long id, String redemptionKey, BigDecimal amount,
                                       CreditNoteRedemptionStatus status, long storeNodeId, Long saleId,
                                       Instant createdAt) {

    public static CreditNoteRedemptionView from(CreditNoteRedemption redemption) {
        return new CreditNoteRedemptionView(redemption.getId(), redemption.getRedemptionKey(),
                redemption.getAmount(), redemption.getStatus(), redemption.getStoreNodeId(), redemption.getSaleId(),
                redemption.getCreatedAt());
    }
}
