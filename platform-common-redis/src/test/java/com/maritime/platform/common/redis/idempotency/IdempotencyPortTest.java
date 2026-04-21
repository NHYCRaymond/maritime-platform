package com.maritime.platform.common.redis.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maritime.platform.common.redis.idempotency.IdempotencyPort.IdempotencyRecord;
import com.maritime.platform.common.redis.idempotency.IdempotencyPort.IdempotencyResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link RedisIdempotencyPort} against a real Redis via Testcontainers.
 */
@Testcontainers
class IdempotencyPortTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static IdempotencyPort port;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        port = new RedisIdempotencyPort(redisTemplate, om, "pe:idem");
    }

    @BeforeEach
    void cleanup() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void findResult_forUnknownKey_returnsEmpty() {
        Optional<IdempotencyResult> r = port.findResult("t1", "missing-key");
        assertThat(r).isEmpty();
    }

    @Test
    void recordResult_thenFind_returnsValue() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        IdempotencyRecord rec = new IdempotencyRecord(
                "t1", "key-A", "{\"ok\":true}", now, "OP_X");

        boolean stored = port.recordResult(rec, Duration.ofMinutes(5));
        assertThat(stored).isTrue();

        Optional<IdempotencyResult> found = port.findResult("t1", "key-A");
        assertThat(found).isPresent();
        assertThat(found.get().resultJson()).isEqualTo("{\"ok\":true}");
        assertThat(found.get().executedAt()).isEqualTo(now);
        assertThat(found.get().operationType()).isEqualTo("OP_X");
    }

    @Test
    void recordResult_twice_secondFails() {
        Instant now = Instant.now();
        IdempotencyRecord first = new IdempotencyRecord("t1", "key-B", "v1", now, "OP");
        IdempotencyRecord second = new IdempotencyRecord("t1", "key-B", "v2", now, "OP");

        assertThat(port.recordResult(first, Duration.ofMinutes(5))).isTrue();
        assertThat(port.recordResult(second, Duration.ofMinutes(5))).isFalse();

        Optional<IdempotencyResult> found = port.findResult("t1", "key-B");
        assertThat(found).isPresent();
        assertThat(found.get().resultJson()).isEqualTo("v1");
    }

    @Test
    void isProcessed_reflectsState() {
        assertThat(port.isProcessed("t1", "key-C")).isFalse();

        port.recordResult(new IdempotencyRecord(
                "t1", "key-C", "{}", Instant.now(), "OP"), Duration.ofMinutes(5));

        assertThat(port.isProcessed("t1", "key-C")).isTrue();
    }

    @Test
    void recordResult_afterTtlExpires_notProcessed() throws Exception {
        port.recordResult(new IdempotencyRecord(
                "t1", "key-D", "{}", Instant.now(), "OP"), Duration.ofMillis(300));

        assertThat(port.isProcessed("t1", "key-D")).isTrue();

        Thread.sleep(500);

        assertThat(port.isProcessed("t1", "key-D")).isFalse();
        assertThat(port.findResult("t1", "key-D")).isEmpty();
    }

    @Test
    void tenantIsolation_sameKeyDifferentTenants() {
        Instant now = Instant.now();
        port.recordResult(new IdempotencyRecord(
                "tenantA", "key-X", "A", now, "OP"), Duration.ofMinutes(5));
        port.recordResult(new IdempotencyRecord(
                "tenantB", "key-X", "B", now, "OP"), Duration.ofMinutes(5));

        assertThat(port.findResult("tenantA", "key-X").map(IdempotencyResult::resultJson))
                .contains("A");
        assertThat(port.findResult("tenantB", "key-X").map(IdempotencyResult::resultJson))
                .contains("B");
    }

    @Test
    void record_rejectsBlankTenant() {
        assertThatThrownBy(() -> new IdempotencyRecord(
                "", "key", "{}", Instant.now(), "OP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void record_rejectsBlankKey() {
        assertThatThrownBy(() -> new IdempotencyRecord(
                "t1", " ", "{}", Instant.now(), "OP"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}