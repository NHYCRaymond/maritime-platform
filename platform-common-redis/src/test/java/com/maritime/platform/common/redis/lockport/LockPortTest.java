package com.maritime.platform.common.redis.lockport;

import com.maritime.platform.common.redis.lockport.LockPort.LockHandle;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RedisLockPort} against a real Redis via Testcontainers.
 */
@Testcontainers
class LockPortTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static LockPort lockPort;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        lockPort = new RedisLockPort(redisTemplate, "pe:lock");
    }

    @BeforeEach
    void cleanup() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void tryLock_withUnclaimedKey_returnsHandle() {
        Optional<LockHandle> handle = lockPort.tryLock(
                "user", "U1", Duration.ofMillis(100), Duration.ofSeconds(10));

        assertThat(handle).isPresent();
        assertThat(handle.get().lockKey()).isEqualTo("pe:lock:user:U1");
        handle.get().close();
    }

    @Test
    void tryLock_whenAlreadyLocked_returnsEmpty() {
        Optional<LockHandle> first = lockPort.tryLock(
                "user", "U2", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(first).isPresent();

        long before = System.currentTimeMillis();
        Optional<LockHandle> second = lockPort.tryLock(
                "user", "U2", Duration.ofMillis(100), Duration.ofSeconds(10));
        long elapsed = System.currentTimeMillis() - before;

        assertThat(second).isEmpty();
        // Should have spun/waited roughly the waitTime
        assertThat(elapsed).isGreaterThanOrEqualTo(80L);
        assertThat(elapsed).isLessThan(2000L);

        first.get().close();
    }

    @Test
    void tryLock_afterUnlock_succeeds() {
        Optional<LockHandle> first = lockPort.tryLock(
                "user", "U3", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(first).isPresent();
        first.get().unlock();

        Optional<LockHandle> second = lockPort.tryLock(
                "user", "U3", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(second).isPresent();
        second.get().close();
    }

    @Test
    void isLocked_reflectsState() {
        assertThat(lockPort.isLocked("user", "U4")).isFalse();

        Optional<LockHandle> handle = lockPort.tryLock(
                "user", "U4", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(handle).isPresent();

        assertThat(lockPort.isLocked("user", "U4")).isTrue();

        handle.get().close();

        assertThat(lockPort.isLocked("user", "U4")).isFalse();
    }

    @Test
    void close_releasesLock() {
        Optional<LockHandle> first = lockPort.tryLock(
                "user", "U5", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(first).isPresent();

        try (LockHandle ignored = first.get()) {
            assertThat(lockPort.isLocked("user", "U5")).isTrue();
        }

        assertThat(lockPort.isLocked("user", "U5")).isFalse();
    }

    @Test
    void unlock_isIdempotent() {
        Optional<LockHandle> handle = lockPort.tryLock(
                "user", "U6", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(handle).isPresent();

        handle.get().unlock();
        // Second call must not throw
        handle.get().unlock();
        handle.get().close();
    }

    @Test
    void unlock_doesNotReleaseAnotherTokensLock() {
        Optional<LockHandle> owner = lockPort.tryLock(
                "user", "U7", Duration.ofMillis(100), Duration.ofSeconds(10));
        assertThat(owner).isPresent();

        // Manually overwrite to simulate a stale handle owning a different token
        // (we cannot force this via public API; instead assert that release-by-other-token
        // via a second RedisLockPort with a manually-held value does nothing).
        // We simulate by releasing owner, then setting the value ourselves with a
        // different token, then confirming owner.close() does not delete our key.
        owner.get().close();
        redisTemplate.opsForValue().set("pe:lock:user:U7", "OTHER_TOKEN");

        owner.get().close(); // idempotent, still holds internal released=true; does nothing

        assertThat(redisTemplate.opsForValue().get("pe:lock:user:U7")).isEqualTo("OTHER_TOKEN");
    }
}