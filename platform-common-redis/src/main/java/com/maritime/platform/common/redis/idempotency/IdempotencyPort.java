package com.maritime.platform.common.redis.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency key storage + lookup primitive. Records the outcome of an
 * operation keyed by {@code (tenantId, idempotencyKey)} so that retried
 * requests can short-circuit with the previous result.
 *
 * <p>Key format: {@code <keyPrefix>:<tenantId>:<idempotencyKey>} (prefix configurable
 * per {@code IdempotencyPort} bean, default {@code "pe:idem"}).</p>
 */
public interface IdempotencyPort {

    /**
     * Retrieve a previously recorded result. Returns empty if not found or the
     * stored value could not be deserialized.
     */
    Optional<IdempotencyResult> findResult(String tenantId, String idempotencyKey);

    /**
     * Record a result for the given key, iff no value exists yet (atomic set-if-absent).
     * The record expires after {@code ttl}.
     *
     * @return {@code true} if stored; {@code false} if the key already exists or the
     *         serialization/Redis call failed
     */
    boolean recordResult(IdempotencyRecord record, Duration ttl);

    /**
     * Check whether a result has been recorded for the given key.
     * Note: advisory — the record may expire or be written immediately after.
     */
    boolean isProcessed(String tenantId, String idempotencyKey);

    /**
     * Input record used when persisting an idempotency result.
     * All non-null fields are trimmed to non-blank; {@code resultJson} and
     * {@code operationType} default to empty string.
     */
    record IdempotencyRecord(
            String tenantId,
            String idempotencyKey,
            String resultJson,
            Instant executedAt,
            String operationType
    ) {
        public IdempotencyRecord {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId cannot be blank");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey cannot be blank");
            }
            if (executedAt == null) {
                executedAt = Instant.now();
            }
            if (operationType == null) {
                operationType = "";
            }
            if (resultJson == null) {
                resultJson = "";
            }
        }
    }

    /**
     * Output record returned by {@link #findResult(String, String)}.
     */
    record IdempotencyResult(
            String resultJson,
            Instant executedAt,
            String operationType
    ) {}
}