package com.maritime.platform.common.redis.lockport;

import java.time.Duration;
import java.util.Optional;

/**
 * Programmatic distributed lock API (alternative to {@code @DistributedLock} annotation).
 *
 * <p>Lock key format: {@code <keyPrefix>:<aggregate>:<resourceId>} (prefix configurable
 * per {@code LockPort} bean, default {@code "pe:lock"}). TTL &le; 30s recommended.
 * Use try-with-resources for release:</p>
 * <pre>{@code
 * Optional<LockHandle> h = lockPort.tryLock("user", userId, Duration.ofSeconds(3), Duration.ofSeconds(10));
 * if (h.isEmpty()) throw new ConcurrentOperationException();
 * try (LockHandle ignored = h.get()) {
 *     // critical section
 * }
 * }</pre>
 */
public interface LockPort {

    /**
     * Try to acquire a lock on {@code aggregate:resourceId}. Retries every 50ms up to
     * {@code waitTime} before giving up.
     *
     * @param aggregate  logical aggregate name (namespaces the key)
     * @param resourceId identifier of the resource being locked
     * @param waitTime   max blocking time before returning empty
     * @param leaseTime  TTL of the lock; caller must complete the critical section within this
     * @return handle if acquired, empty otherwise
     */
    Optional<LockHandle> tryLock(String aggregate, String resourceId, Duration waitTime, Duration leaseTime);

    /**
     * Check whether a lock currently exists for {@code aggregate:resourceId}.
     * Note: result is advisory — the lock may be released/acquired immediately after.
     */
    boolean isLocked(String aggregate, String resourceId);

    /**
     * Handle to a held lock. Release via {@link #unlock()} or {@link #close()}
     * (AutoCloseable, for try-with-resources). Idempotent.
     */
    interface LockHandle extends AutoCloseable {
        /** Full lock key (includes prefix, aggregate, resourceId). */
        String lockKey();

        /** Release the lock. Safe to call multiple times. */
        void unlock();

        @Override
        void close();
    }
}