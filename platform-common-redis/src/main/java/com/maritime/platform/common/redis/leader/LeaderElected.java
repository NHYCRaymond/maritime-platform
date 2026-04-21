package com.maritime.platform.common.redis.leader;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Scheduled} method (or any method invoked periodically across nodes)
 * to run only on the "leader" instance — the node that acquires the named distributed
 * lock. Other instances silently skip (or log depending on {@link #silentSkip()}).
 *
 * <p>Sample:</p>
 * <pre>{@code
 * @Scheduled(fixedDelay = 30_000)
 * @LeaderElected(name = "countersign-completion-scan")
 * public void scan() { ... }
 * }</pre>
 *
 * <p>Backed by {@code LockPort}; lock key is {@code "leader:<name>"} under the
 * lock namespace (default {@code pe:lock:leader:<name>}).</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LeaderElected {

    /** Logical leader name; full lock key resolves to {@code leader:<name>}. */
    String name();

    /** Max time to block waiting for the lock, in milliseconds. Default 5000ms. */
    long waitMillis() default 5000;

    /**
     * Lock lease TTL in milliseconds; the leader's method body must complete within
     * this window or another node may claim the lock. Default 25000ms (below the
     * recommended 30s ceiling).
     */
    long leaseMillis() default 25000;

    /**
     * When true, log skipped executions at {@code DEBUG}; when false, at {@code INFO}.
     * Default {@code true}.
     */
    boolean silentSkip() default true;
}