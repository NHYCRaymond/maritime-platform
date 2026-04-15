package com.maritime.platform.common.core.audit;

/**
 * Resolve the current operator user id and system code.
 * Typically reads from a SecurityContext / ThreadLocal populated by the gateway or a filter.
 * A default fallback bean returns "SYSTEM"/"UNKNOWN" when no context is present.
 */
public interface AuditOperatorResolver {
    String currentUserId();
    String currentSystemCode();
}