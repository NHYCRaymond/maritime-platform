package com.maritime.platform.common.outbox.spi;

import com.maritime.platform.common.outbox.dataobject.OutboxEntryDO;

/**
 * Application-provided adapter: publishes an outbox entry's payload to the
 * messaging system (RabbitMQ/Kafka/etc). Must be idempotent — the poller
 * may re-attempt on transient failures.
 */
public interface OutboxEventPublisher {
    void publish(OutboxEntryDO entry) throws Exception;
}