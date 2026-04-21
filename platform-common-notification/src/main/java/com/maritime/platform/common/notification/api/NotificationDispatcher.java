package com.maritime.platform.common.notification.api;

/**
 * Core contract for dispatching a notification across one or more {@link Channel}s.
 *
 * <p>Implementations must be thread-safe. Idempotency is honoured when
 * {@link NotificationRequest#idempotencyKey()} is non-null; each implementation
 * decides its own de-duplication strategy (in-memory, persistent, none).</p>
 */
public interface NotificationDispatcher {

    /**
     * Dispatch a notification. Idempotent if {@code request.idempotencyKey} is set
     * (implementation decides whether to de-dupe).
     * Thread-safe.
     *
     * @param request the notification request; must not be {@code null}
     * @return the outcome of the dispatch
     */
    DispatchResult dispatch(NotificationRequest request);
}