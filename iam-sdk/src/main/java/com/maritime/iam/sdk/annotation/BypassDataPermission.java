package com.maritime.iam.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method (or type) whose invocations should skip iam-sdk
 * data-permission SQL injection. Typical use: {@code @Scheduled}
 * jobs, RabbitMQ {@code @RabbitListener} consumers, internal
 * system calls that have no IAM context and that must operate on
 * the full row set.
 *
 * <p>When the aspect (auto-wired by
 * {@link com.maritime.iam.sdk.IamSdkAutoConfiguration}) sees this
 * annotation, it wraps the call in
 * {@link com.maritime.iam.sdk.context.SystemContext#scoped()}.</p>
 *
 * <p>Applying it to a type has the same effect as applying it to
 * every public method of that type.</p>
 *
 * <p><strong>Use sparingly.</strong> Bypassing data-permission is
 * a privilege escalation — annotate only code paths that are
 * operator- or scheduler-initiated, never user-driven.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BypassDataPermission {

    /** Optional audit reason shown in logs / code review. */
    String reason() default "";
}
