package com.medinasystems.pos.sales.application;

import static com.medinasystems.pos.sync.UploadPayload.text;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: a store that could not release the claims of a failed return sends the release later. The release is
 * idempotent: a key with nothing reserved for this store is a no-op, so the item is accepted.
 */
@Component
@Profile("central")
public class ReturnClaimReleaseUploadHandler implements UploadHandler {

    static final String EVENT_TYPE = "RETURN_CLAIM_RELEASE";

    private final ReturnClaimService claims;

    public ReturnClaimReleaseUploadHandler(ReturnClaimService claims) {
        this.claims = claims;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        String claimKey;
        try {
            claimKey = text(payload, "claimKey");
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        claims.release(claimKey, storeNodeId);
        return UploadOutcome.accepted();
    }
}
