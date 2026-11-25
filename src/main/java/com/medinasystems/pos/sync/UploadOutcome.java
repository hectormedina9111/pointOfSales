package com.medinasystems.pos.sync;

/**
 * What a handler decided for one upload item. ACCEPTED and REJECTED are final and kept in the receipt. RETRY means the
 * item cannot be applied yet, for example because its cash session has not arrived: nothing is kept and the store
 * sends the item again later.
 *
 * @param status ACCEPTED, REJECTED or RETRY
 * @param code   explanation such as STALE or BRANCH_MISMATCH; null when there is nothing to add
 */
public record UploadOutcome(Status status, String code) {

    public enum Status {
        ACCEPTED, REJECTED, RETRY
    }

    public static UploadOutcome accepted() {
        return new UploadOutcome(Status.ACCEPTED, null);
    }

    public static UploadOutcome accepted(String code) {
        return new UploadOutcome(Status.ACCEPTED, code);
    }

    public static UploadOutcome rejected(String code) {
        return new UploadOutcome(Status.REJECTED, code);
    }

    public static UploadOutcome retry(String code) {
        return new UploadOutcome(Status.RETRY, code);
    }
}
