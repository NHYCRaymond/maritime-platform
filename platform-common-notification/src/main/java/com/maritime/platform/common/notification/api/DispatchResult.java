package com.maritime.platform.common.notification.api;

/**
 * Sealed outcome of a {@link NotificationDispatcher#dispatch(NotificationRequest)} call.
 *
 * <p>Exactly one of the nested records is returned. Callers should switch on the
 * concrete type rather than inspecting message strings.</p>
 */
public sealed interface DispatchResult permits
        DispatchResult.Success,
        DispatchResult.Failed,
        DispatchResult.SkippedDuplicate {

    /** Dispatch succeeded on every requested channel. */
    record Success(String dispatchId) implements DispatchResult {}

    /**
     * Dispatch failed on one or more channels.
     *
     * @param reason human-readable aggregate reason, typically listing failed channels
     * @param cause  nullable root throwable
     */
    record Failed(String reason, Throwable cause) implements DispatchResult {
        public Failed(String reason) { this(reason, null); }
    }

    /** Dispatch skipped because an equivalent request (same idempotency key) was already handled. */
    record SkippedDuplicate(String idempotencyKey) implements DispatchResult {}
}
