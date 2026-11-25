package com.medinasystems.pos.sync;

/**
 * Public API of the sync module for producers. Queues a document or a change to be uploaded to the central node.
 *
 * <p>Only a store queues rows. The central node has this bean too, and calling it there throws. The row is written
 * in the transaction of the caller, so the document and its upload are saved together or not at all.
 */
public interface Outbox {

    /**
     * Queues an upload. The payload is serialized to JSON with the application's mapper.
     *
     * @param eventType event name the central node dispatches on, such as SALE_PAID
     * @param payload   object whose JSON is the body of the event; its field names are part of the protocol
     * @throws IllegalStateException when called on the central node
     */
    void enqueue(String eventType, Object payload);
}
