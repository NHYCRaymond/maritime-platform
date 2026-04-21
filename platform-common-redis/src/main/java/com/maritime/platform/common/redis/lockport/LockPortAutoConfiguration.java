package com.maritime.platform.common.redis.lockport;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configures a default {@link LockPort} bean backed by {@link RedisLockPort}
 * when a {@link StringRedisTemplate} is available.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class LockPortAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LockPort lockPort(StringRedisTemplate redis) {
        return new RedisLockPort(redis, "pe:lock");
    }
}
