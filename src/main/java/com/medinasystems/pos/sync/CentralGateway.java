package com.medinasystems.pos.sync;

/**
 * Public API of the sync module for online calls from a store to the central node. Only a store can call the central
 * node; the store always starts the call, and it uses its own store token and short timeouts.
 *
 * <p>Failures are translated: when the central node cannot be reached, or answers 5xx or 401/403, the call throws
 * {@link CentralUnavailableException}. A problem answer keeps the central node's code and status: 404 becomes
 * {@link com.medinasystems.pos.shared.BusinessNotFoundException}, 409 a
 * {@link com.medinasystems.pos.shared.BusinessConflictException} and any other 4xx a
 * {@link com.medinasystems.pos.shared.BusinessRuleException}.
 *
 * <p>Paths are absolute, start with {@code /api/v1/}, and contain only characters that need no escaping: the callers
 * validate the values they put in them. The central node has this bean too, and calling it there throws.
 */
public interface CentralGateway {

    /**
     * @param path absolute path on the central node, such as {@code /api/v1/sync/credit-notes/NC-000001}
     * @param type the type of the JSON answer
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    <T> T get(String path, Class<T> type);

    /**
     * @param path absolute path on the central node
     * @param body the JSON body, or null for a call without body
     * @param type the type of the JSON answer
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    <T> T post(String path, Object body, Class<T> type);
}
