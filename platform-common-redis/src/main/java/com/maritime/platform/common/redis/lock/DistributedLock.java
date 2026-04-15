package com.maritime.platform.common.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative distributed lock. Acquires a Redis-backed lock before the annotated
 * method executes; releases it in a finally block.
 *
 * <p>Key supports SpEL against method arguments:
 * {@code @DistributedLock(key = "order:#orderId")}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** SpEL expression or literal for the lock key. */
    String key();

    /** Key prefix prepended to the evaluated key. Default: "lock:". */
    String prefix() default "lock:";

    /** Max time to wait while attempting acquisition. "0" means tryLock once. */
    String waitTime() default "0";

    /** Lock TTL. Must exceed expected critical-section duration. */
    String leaseTime() default "30s";
}