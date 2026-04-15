package com.maritime.platform.common.outbox.poller;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("platform.outbox")
public class OutboxProperties {
    /** Whether outbox polling is enabled. */
    private boolean enabled = true;
    /** Poll interval. */
    private Duration pollInterval = Duration.ofSeconds(5);
    /** Max entries fetched per poll. */
    private int batchSize = 100;
    /** Max retry attempts before giving up (entry stays FAILED, no more retries). */
    private int maxRetries = 10;
    /** Base backoff; actual delay = base * 2^(retryCount-1), capped at maxBackoff. */
    private Duration baseBackoff = Duration.ofSeconds(5);
    private Duration maxBackoff = Duration.ofMinutes(10);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getBaseBackoff() { return baseBackoff; }
    public void setBaseBackoff(Duration baseBackoff) { this.baseBackoff = baseBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
}