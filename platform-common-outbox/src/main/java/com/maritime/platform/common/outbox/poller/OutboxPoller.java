package com.maritime.platform.common.outbox.poller;

import com.maritime.platform.common.outbox.dataobject.OutboxEntryDO;
import com.maritime.platform.common.outbox.spi.OutboxEventPublisher;
import com.maritime.platform.common.outbox.store.OutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxStore store;
    private final OutboxEventPublisher publisher;
    private final OutboxProperties props;

    public OutboxPoller(OutboxStore store, OutboxEventPublisher publisher, OutboxProperties props) {
        this.store = store;
        this.publisher = publisher;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "#{@outboxProperties.pollInterval.toMillis()}")
    public void poll() {
        if (!props.isEnabled()) return;
        List<OutboxEntryDO> due = store.findDue(props.getBatchSize());
        for (OutboxEntryDO entry : due) {
            try {
                publisher.publish(entry);
                store.markPublished(entry.getId());
            } catch (Exception e) {
                int nextRetry = (entry.getRetryCount() == null ? 0 : entry.getRetryCount()) + 1;
                if (nextRetry > props.getMaxRetries()) {
                    log.error("Outbox entry {} exhausted retries ({}), leaving FAILED permanently",
                            entry.getId(), nextRetry);
                    store.markFailed(entry.getId(), nextRetry, null, e.getMessage());
                    continue;
                }
                Duration backoff = computeBackoff(nextRetry);
                LocalDateTime nextAt = LocalDateTime.now().plus(backoff);
                log.warn("Outbox entry {} publish failed (retry {}/{}, next in {}): {}",
                        entry.getId(), nextRetry, props.getMaxRetries(), backoff, e.getMessage());
                store.markFailed(entry.getId(), nextRetry, nextAt, e.getMessage());
            }
        }
    }

    private Duration computeBackoff(int retryCount) {
        long baseMillis = props.getBaseBackoff().toMillis();
        long computed = baseMillis * (1L << Math.min(retryCount - 1, 20));
        long capped = Math.min(computed, props.getMaxBackoff().toMillis());
        return Duration.ofMillis(capped);
    }
}