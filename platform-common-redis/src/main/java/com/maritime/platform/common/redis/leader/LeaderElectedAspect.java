package com.maritime.platform.common.redis.leader;

import com.maritime.platform.common.redis.lockport.LockPort;
import com.maritime.platform.common.redis.lockport.LockPort.LockHandle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * AOP interceptor implementing the {@link LeaderElected} contract. Acquires the
 * named distributed lock via {@link LockPort} before invoking the method body;
 * if the lock is held by another instance, skips the call (returns {@code null}
 * for reference returns; void methods proceed as no-ops).
 */
@Aspect
public class LeaderElectedAspect {

    private static final Logger log = LoggerFactory.getLogger(LeaderElectedAspect.class);

    private final LockPort lockPort;

    public LeaderElectedAspect(LockPort lockPort) {
        this.lockPort = lockPort;
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, LeaderElected annotation) throws Throwable {
        Optional<LockHandle> handle = lockPort.tryLock(
                "leader",
                annotation.name(),
                Duration.ofMillis(annotation.waitMillis()),
                Duration.ofMillis(annotation.leaseMillis()));

        if (handle.isEmpty()) {
            if (annotation.silentSkip()) {
                log.debug("@LeaderElected[{}] skipped — another instance holds the lock", annotation.name());
            } else {
                log.info("@LeaderElected[{}] skipped — another instance holds the lock", annotation.name());
            }
            return null;
        }

        try (LockHandle ignored = handle.get()) {
            return pjp.proceed();
        }
    }
}