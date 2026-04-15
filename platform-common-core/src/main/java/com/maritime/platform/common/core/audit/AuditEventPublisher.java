package com.maritime.platform.common.core.audit;

import com.maritime.platform.common.core.event.AuditEvent;

/**
 * Application-provided sink for @OperationAudit aspect output.
 * Implementations may publish to MQ (outbox), write to DB, or ship via HTTP.
 */
public interface AuditEventPublisher {
    void publish(AuditEvent event);
}