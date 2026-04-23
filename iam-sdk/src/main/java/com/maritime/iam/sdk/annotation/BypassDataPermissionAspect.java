package com.maritime.iam.sdk.annotation;

import com.maritime.iam.sdk.context.SystemContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP bridge: methods (or types) annotated with
 * {@link BypassDataPermission} run inside a
 * {@link SystemContext} scope.
 *
 * <p>Auto-registered by
 * {@link com.maritime.iam.sdk.IamSdkAutoConfiguration} when
 * Spring AOP is on the classpath. The aspect is
 * Spring-managed, not AspectJ-weaved, so only Spring-proxied
 * beans trigger it (which matches the usual service / scheduled
 * / listener call sites).</p>
 */
@Aspect
public class BypassDataPermissionAspect {

    @Around("@annotation(com.maritime.iam.sdk.annotation.BypassDataPermission)"
            + " || @within(com.maritime.iam.sdk.annotation.BypassDataPermission)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        SystemContext.enter();
        try {
            return pjp.proceed();
        } finally {
            SystemContext.exit();
        }
    }
}
