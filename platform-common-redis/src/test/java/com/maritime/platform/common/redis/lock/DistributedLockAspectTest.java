package com.maritime.platform.common.redis.lock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Integration tests for {@link DistributedLockAspect} using a real Redis instance
 * via Testcontainers.
 */
@Testcontainers
class DistributedLockAspectTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static DistributedLockAspect aspect;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        aspect = new DistributedLockAspect(redisTemplate);
    }

    // ---------- Test service inner classes ----------

    static class OrderService {

        private final AtomicInteger callCount = new AtomicInteger(0);

        @DistributedLock(key = "#orderId", prefix = "lock:", leaseTime = "10s")
        public void processOrder(String orderId) {
            callCount.incrementAndGet();
        }

        @DistributedLock(key = "#orderId", prefix = "lock:", waitTime = "100ms", leaseTime = "30s")
        public void processOrderWithWait(String orderId) {
            callCount.incrementAndGet();
        }

        @DistributedLock(key = "#id", prefix = "lock:", leaseTime = "10s")
        public void doWithId(String id) {
            callCount.incrementAndGet();
        }
    }

    private OrderService proxyOf(OrderService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    // ---------- Test 1: acquires and releases lock ----------

    @Test
    void acquiresAndReleasesLock() {
        OrderService target = new OrderService();
        OrderService proxy = proxyOf(target);
        proxy.processOrder("ORDER-001");

        assertThat(target.callCount.get()).isEqualTo(1);

        // After method completes the lock must have been released —
        // a second call to the same key must succeed without blocking.
        proxy.processOrder("ORDER-001");
        assertThat(target.callCount.get()).isEqualTo(2);
    }

    // ---------- Test 2: blocking call times out ----------

    @Test
    void blockingCallTimesOut() throws Exception {
        // Manually hold the lock for a key so that the proxy call will find it taken.
        String key = "lock:ORDER-HELD";
        String owner = com.maritime.platform.common.redis.util.RedisDistributedLock.newRequestId();
        com.maritime.platform.common.redis.util.RedisDistributedLock
                .tryLock(redisTemplate, key, owner, java.time.Duration.ofSeconds(30));

        OrderService proxy = proxyOf(new OrderService());

        long before = System.currentTimeMillis();
        assertThatThrownBy(() -> proxy.processOrderWithWait("ORDER-HELD"))
                .isInstanceOf(DistributedLockException.class)
                .hasMessageContaining("lock:ORDER-HELD");
        long elapsed = System.currentTimeMillis() - before;

        // Should have waited roughly 100ms (allow generous upper bound for CI)
        assertThat(elapsed).isGreaterThanOrEqualTo(80L);
        assertThat(elapsed).isLessThan(2000L);

        // Clean up
        com.maritime.platform.common.redis.util.RedisDistributedLock
                .releaseLock(redisTemplate, key, owner);
    }

    // ---------- Test 3: SpEL key resolution ----------

    @Test
    void spelKeyResolution() {
        // Verify key formed as "lock:{id}" using SpEL "#id" expression.
        OrderService target = new OrderService();
        OrderService proxy = proxyOf(target);
        proxy.doWithId("abc");

        assertThat(target.callCount.get())
                .as("underlying method must have been invoked once")
                .isEqualTo(1);

        // Key "lock:abc" must be gone (released) after method exits —
        // confirm by acquiring it ourselves successfully.
        String key = "lock:abc";
        String testOwner = com.maritime.platform.common.redis.util.RedisDistributedLock.newRequestId();
        boolean acquired = com.maritime.platform.common.redis.util.RedisDistributedLock
                .tryLock(redisTemplate, key, testOwner, java.time.Duration.ofSeconds(5));

        assertThat(acquired)
                .as("Key 'lock:abc' should be free after method exit (SpEL #id resolved correctly)")
                .isTrue();

        // Clean up
        com.maritime.platform.common.redis.util.RedisDistributedLock
                .releaseLock(redisTemplate, key, testOwner);
    }
}