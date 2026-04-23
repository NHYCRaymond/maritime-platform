package com.maritime.iam.sdk.context;

/**
 * Per-thread flag that tells
 * {@link com.maritime.iam.sdk.dataperm.DataPermissionInjector}
 * to skip WHERE-clause injection entirely.
 *
 * <p>Intended for <em>system-initiated</em> code paths that run
 * outside an HTTP request (scheduled jobs, MQ consumers, outbox
 * pollers, CLI tools). Such paths have no {@code IamContext}
 * populated, so without this flag the SDK's
 * {@code LineRoleFilter} emits {@code 1 = 0} and silently
 * filters out every row — usually manifesting as "the scheduler
 * sees 0 rows and my tickets never close".
 *
 * <p>Depth-counted to allow safe nesting:</p>
 * <pre>
 * SystemContext.enter();
 * try {
 *     // outer system scope
 *     somethingThatAlsoEntersSystemContext();
 * } finally {
 *     SystemContext.exit();
 * }
 * </pre>
 *
 * <p>Prefer {@link #scoped()} with try-with-resources, or the
 * {@code @BypassDataPermission} annotation on the outer
 * method.</p>
 *
 * @see com.maritime.iam.sdk.annotation.BypassDataPermission
 */
public final class SystemContext {

    private static final ThreadLocal<Integer> DEPTH =
            new ThreadLocal<>();

    private SystemContext() {
    }

    /** Enters (or nests) a system scope on the current thread. */
    public static void enter() {
        Integer d = DEPTH.get();
        DEPTH.set(d == null ? 1 : d + 1);
    }

    /**
     * Exits one level of system scope. Matches {@link #enter()}.
     * When depth returns to zero the ThreadLocal is removed.
     */
    public static void exit() {
        Integer d = DEPTH.get();
        if (d == null) {
            return;
        }
        if (d <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(d - 1);
        }
    }

    /** True when any system scope is active on this thread. */
    public static boolean isActive() {
        return DEPTH.get() != null;
    }

    /**
     * Try-with-resources helper:
     * <pre>
     * try (var ignored = SystemContext.scoped()) {
     *     // system-privileged block
     * }
     * </pre>
     */
    public static AutoCloseable scoped() {
        enter();
        return SystemContext::exit;
    }
}
