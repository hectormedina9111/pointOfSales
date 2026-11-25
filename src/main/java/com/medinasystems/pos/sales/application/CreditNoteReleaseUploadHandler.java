package com.medinasystems.pos.sales.application;

import static com.medinasystems.pos.sync.UploadPayload.text;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sales.application.CreditNoteRedemptionService.ReleaseOutcome;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: a store that could not release a failed payment right away sends the release later. The release is
 * idempotent, so a release that arrives after the central node already released the key is accepted. A redemption that
 * a sale has already confirmed cannot be released: that item is rejected.
 */
@Component
@Profile("central")
public class CreditNoteReleaseUploadHandler implements UploadHandler {

    static final String EVENT_TYPE = "CREDIT_NOTE_RELEASE";

    private final CreditNoteRedemptionService redemptions;

    public CreditNoteReleaseUploadHandler(CreditNoteRedemptionService redemptions) {
        this.redemptions = redemptions;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        String redemptionKey;
        try {
            redemptionKey = text(payload, "redemptionKey");
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (redemptions.release(redemptionKey, storeNodeId) == ReleaseOutcome.CONFIRMED) {
            return UploadOutcome.rejected("CREDIT_NOTE_REDEMPTION_CONFIRMED");
        }
        return UploadOutcome.accepted();
    }
}
