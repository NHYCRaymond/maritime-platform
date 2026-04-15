package com.maritime.platform.common.redis.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configures {@link DistributedLockAspect} when Spring AOP and
 * a {@link StringRedisTemplate} bean are both present on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({ org.aspectj.lang.ProceedingJoinPoint.class, StringRedisTemplate.class })
@ConditionalOnBean(StringRedisTemplate.class)
public class DistributedLockAutoConfiguration {

    @Bean
    public DistributedLockAspect distributedLockAspect(StringRedisTemplate redis) {
        return new DistributedLockAspect(redis);
    }
}