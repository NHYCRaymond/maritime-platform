package com.maritime.platform.common.redis.lock;

import com.maritime.platform.common.redis.util.RedisDistributedLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * AOP aspect that intercepts {@link DistributedLock}-annotated methods,
 * acquires a Redis-backed distributed lock, and releases it in a finally block.
 *
 * <p>Acquisition strategy:
 * <ul>
 *   <li>If {@code waitTime == 0}: single tryLock attempt; throws on failure.</li>
 *   <li>If {@code waitTime > 0}: polls with exponential backoff (50ms → 500ms cap)
 *       until the deadline is reached, then throws {@link DistributedLockException}.</li>
 * </ul>
 */
@Aspect
public class DistributedLockAspect {

    private final StringRedisTemplate redis;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Around("@annotation(lockAnno)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lockAnno) throws Throwable {
        String key = lockAnno.prefix() + resolveKey(pjp, lockAnno.key());
        Duration waitTime = DurationStyle.detectAndParse(lockAnno.waitTime());
        Duration leaseTime = DurationStyle.detectAndParse(lockAnno.leaseTime());
        String owner = RedisDistributedLock.newRequestId();

        if (!acquire(key, owner, waitTime, leaseTime)) {
            throw new DistributedLockException(
                    "Failed to acquire lock '" + key + "' within " + waitTime);
        }
        try {
            return pjp.proceed();
        } finally {
            RedisDistributedLock.releaseLock(redis, key, owner);
        }
    }

    private boolean acquire(String key, String owner, Duration waitTime, Duration leaseTime)
            throws InterruptedException {
        if (RedisDistributedLock.tryLock(redis, key, owner, leaseTime)) {
            return true;
        }
        if (waitTime.isZero() || waitTime.isNegative()) {
            return false;
        }
        long deadline = System.nanoTime() + waitTime.toNanos();
        long backoff = 50L;
        while (System.nanoTime() < deadline) {
            long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
            Thread.sleep(Math.min(backoff, Math.max(1L, remainingMs)));
            if (RedisDistributedLock.tryLock(redis, key, owner, leaseTime)) {
                return true;
            }
            backoff = Math.min(backoff * 2, 500L);
        }
        return false;
    }

    private String resolveKey(ProceedingJoinPoint pjp, String keyExpression) {
        if (!keyExpression.contains("#") && !keyExpression.contains("'")) {
            return keyExpression;
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = paramDiscoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();
        EvaluationContext ctx = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        Expression expr = parser.parseExpression(keyExpression);
        Object value = expr.getValue(ctx);
        return value == null ? "null" : value.toString();
    }
}