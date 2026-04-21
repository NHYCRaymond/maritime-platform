package com.maritime.platform.common.redis.lockport;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed {@link LockPort} implementation using {@code SET NX EX} + a Lua release
 * script that verifies ownership token. No external dependency beyond
 * {@link StringRedisTemplate}.
 */
public class RedisLockPort implements LockPort {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final long RETRY_INTERVAL_MILLIS = 50L;

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisLockPort(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    private String key(String aggregate, String resourceId) {
        return keyPrefix + ":" + aggregate + ":" + resourceId;
    }

    @Override
    public Optional<LockHandle> tryLock(String aggregate, String resourceId, Duration waitTime, Duration leaseTime) {
        String k = key(aggregate, resourceId);
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitTime.toMillis();

        do {
            Boolean ok = redis.opsForValue().setIfAbsent(k, token, leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(ok)) {
                return Optional.of(new RedisLockHandle(k, token));
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        } while (System.currentTimeMillis() < deadline);

        return Optional.empty();
    }

    @Override
    public boolean isLocked(String aggregate, String resourceId) {
        Boolean exists = redis.hasKey(key(aggregate, resourceId));
        return Boolean.TRUE.equals(exists);
    }

    private class RedisLockHandle implements LockHandle {
        private final String key;
        private final String token;
        private volatile boolean released = false;

        RedisLockHandle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public String lockKey() {
            return key;
        }

        @Override
        public synchronized void unlock() {
            if (released) {
                return;
            }
            released = true;
            try {
                redis.execute(unlockScript, Collections.singletonList(key), token);
            } catch (Exception e) {
                // best-effort: lease TTL will clean up eventually
            }
        }

        @Override
        public void close() {
            unlock();
        }
    }
}