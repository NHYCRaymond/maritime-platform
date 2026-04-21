package com.maritime.platform.common.redis.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed {@link IdempotencyPort} implementation. Serializes the result
 * record as JSON and stores it with an absolute TTL.
 */
public class RedisIdempotencyPort implements IdempotencyPort {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String keyPrefix;

    public RedisIdempotencyPort(StringRedisTemplate redis, ObjectMapper json, String keyPrefix) {
        this.redis = redis;
        this.json = json;
        this.keyPrefix = keyPrefix;
    }

    private String key(String tenantId, String idempotencyKey) {
        return keyPrefix + ":" + tenantId + ":" + idempotencyKey;
    }

    @Override
    public Optional<IdempotencyResult> findResult(String tenantId, String idempotencyKey) {
        String v = redis.opsForValue().get(key(tenantId, idempotencyKey));
        if (v == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(v, IdempotencyResult.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean recordResult(IdempotencyRecord record, Duration ttl) {
        try {
            String v = json.writeValueAsString(new IdempotencyResult(
                    record.resultJson(), record.executedAt(), record.operationType()));
            Boolean set = redis.opsForValue().setIfAbsent(
                    key(record.tenantId(), record.idempotencyKey()),
                    v,
                    ttl.toMillis(),
                    TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(set);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isProcessed(String tenantId, String idempotencyKey) {
        Boolean exists = redis.hasKey(key(tenantId, idempotencyKey));
        return Boolean.TRUE.equals(exists);
    }
}