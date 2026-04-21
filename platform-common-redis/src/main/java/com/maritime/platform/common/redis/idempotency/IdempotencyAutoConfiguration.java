package com.maritime.platform.common.redis.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configures a default {@link IdempotencyPort} bean backed by
 * {@link RedisIdempotencyPort} when a {@link StringRedisTemplate} and an
 * {@link ObjectMapper} are available.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean({StringRedisTemplate.class, ObjectMapper.class})
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyPort idempotencyPort(StringRedisTemplate redis, ObjectMapper om) {
        return new RedisIdempotencyPort(redis, om, "pe:idem");
    }
}