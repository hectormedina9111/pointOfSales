package com.medinasystems.pos.sync;

import tools.jackson.databind.JsonNode;

/**
 * Public SPI of the sync module, implemented by the modules that receive uploads on the central node. Each handler
 * owns one event type. The central node calls it inside the transaction of the item, together with the receipt, so an
 * item is applied once even when the store sends it again.
 */
public interface UploadHandler {

    /** Event type this handler applies, such as SALE_PAID. Each event type has exactly one handler. */
    String eventType();

    /**
     * Applies one item. The handler checks completeness and the branch; it does not revalidate business rules. It must
     * not write anything before it has read and checked the whole payload, and it must answer RETRY before writing.
     *
     * @param storeNodeId   store node that sent the item
     * @param storeBranchId branch of that store, or null when it has none
     * @param payload       the JSON the store queued
     * @return the outcome; a RETRY leaves no receipt, so the store sends the item again in a later cycle
     */
    UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload);
}
