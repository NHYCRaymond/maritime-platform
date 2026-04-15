package com.maritime.platform.common.redis.lock;

/**
 * Thrown when a {@link DistributedLock}-annotated method fails to acquire
 * the Redis-backed lock within the configured wait time.
 */
public class DistributedLockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DistributedLockException(String message) {
        super(message);
    }

    public DistributedLockException(String message, Throwable cause) {
        super(message, cause);
    }
}