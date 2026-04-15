package com.maritime.platform.common.outbox.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.maritime.platform.common.mybatis.dataobject.BaseDO;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("platform_outbox_entry")
public class OutboxEntryDO extends BaseDO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;        // JSON string
    private String status;         // OutboxEntryStatus name
    private Integer retryCount;
    private LocalDateTime publishedAt;
    private LocalDateTime nextRetryAt;
    private String lastError;

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}