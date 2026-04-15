package com.maritime.platform.common.core.audit;

import com.maritime.platform.common.core.annotation.OperationAudit;
import com.maritime.platform.common.core.event.AuditEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Intercepts @OperationAudit methods; publishes an AuditEvent AFTER the
 * target method returns successfully. Thrown exceptions do NOT emit an
 * audit record (the business operation did not actually happen).
 *
 * <p>Detail JSON is left as {@code null} by default — applications can override
 * the aspect to attach arg or return-value details. Keeping it generic avoids
 * leaking sensitive fields into audit storage.
 */
@Aspect
public class OperationAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditAspect.class);

    private final AuditEventPublisher publisher;
    private final AuditOperatorResolver operatorResolver;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    public OperationAuditAspect(AuditEventPublisher publisher,
                                AuditOperatorResolver operatorResolver) {
        this.publisher = publisher;
        this.operatorResolver = operatorResolver;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint pjp, OperationAudit audit) throws Throwable {
        Object result = pjp.proceed();
        try {
            String targetCode = resolveTargetCode(pjp, audit.targetCodeExpr());
            String batchId = audit.batch() ? UUID.randomUUID().toString() : null;
            AuditEvent event = new AuditEvent(
                    null,
                    operatorResolver.currentUserId(),
                    audit.operationType(),
                    operatorResolver.currentSystemCode(),
                    audit.targetType(),
                    targetCode,
                    null,
                    batchId,
                    LocalDateTime.now()
            );
            publisher.publish(event);
        } catch (Exception e) {
            // Never let audit failures break the business flow
            log.warn("Failed to publish audit event for {}: {}",
                    audit.operationType(), e.getMessage());
        }
        return result;
    }

    private String resolveTargetCode(ProceedingJoinPoint pjp, String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
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
        try {
            Expression parsed = parser.parseExpression(expr);
            Object v = parsed.getValue(ctx);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            log.warn("SpEL evaluation failed for '{}': {}", expr, e.getMessage());
            return null;
        }
    }
}