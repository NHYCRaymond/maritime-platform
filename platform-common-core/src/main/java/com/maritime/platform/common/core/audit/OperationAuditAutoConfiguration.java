package com.maritime.platform.common.core.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(org.aspectj.lang.ProceedingJoinPoint.class)
@ConditionalOnBean(AuditEventPublisher.class)
public class OperationAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditOperatorResolver auditOperatorResolver() {
        return new DefaultAuditOperatorResolver();
    }

    @Bean
    public OperationAuditAspect operationAuditAspect(AuditEventPublisher publisher,
                                                     AuditOperatorResolver resolver) {
        return new OperationAuditAspect(publisher, resolver);
    }
}