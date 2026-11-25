package com.medinasystems.pos.sync.application;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.security.RemotePasswordChanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/** Central side: applies a PASSWORD_CHANGED uploaded by a store, keeping the most recent change of each user. */
@Component
@Profile("central")
public class PasswordChangeUploadHandler implements UploadHandler {

    /** Column size of app_user.password_hash. */
    static final int MAX_PASSWORD_HASH = 255;

    private final RemotePasswordChanges passwords;

    public PasswordChangeUploadHandler(RemotePasswordChanges passwords) {
        this.passwords = passwords;
    }

    @Override
    public String eventType() {
        return PasswordChangeOutboxListener.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        PasswordChange change;
        try {
            change = PasswordChange.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        // A store without a branch serves no user, so it must not change any password.
        if (storeBranchId == null) {
            return UploadOutcome.rejected("BRANCH_NOT_ALLOWED");
        }
        return switch (passwords.apply(change.userId(), change.passwordHash(), change.changedAt(), storeBranchId)) {
            case APPLIED -> UploadOutcome.accepted();
            case STALE -> UploadOutcome.accepted("STALE");
            case USER_NOT_FOUND -> UploadOutcome.rejected("USER_NOT_FOUND");
            case BRANCH_NOT_ALLOWED -> UploadOutcome.rejected("BRANCH_NOT_ALLOWED");
        };
    }

    /** The fields of a PASSWORD_CHANGED payload. Built only when every field is present and well formed. */
    private record PasswordChange(long userId, String passwordHash, Instant changedAt) {

        static PasswordChange from(JsonNode payload) {
            long userId = UploadPayload.longField(payload, "userId");
            String hash = UploadPayload.text(payload, "passwordHash");
            if (hash.length() > MAX_PASSWORD_HASH) {
                throw new InvalidPayloadException("passwordHash");
            }
            return new PasswordChange(userId, hash, UploadPayload.instant(payload, "changedAt"));
        }
    }
}
