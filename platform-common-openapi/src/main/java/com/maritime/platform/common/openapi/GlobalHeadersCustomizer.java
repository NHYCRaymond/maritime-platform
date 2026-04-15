package com.maritime.platform.common.openapi;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers global header parameters (X-Trace-ID, X-Tenant-ID) on every operation
 * when enabled via properties. Uses SpringDoc's OperationCustomizer.
 */
@AutoConfiguration(after = PlatformOpenApiAutoConfiguration.class)
@ConditionalOnClass(OperationCustomizer.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class GlobalHeadersCustomizer {

    @Bean
    @ConditionalOnProperty(prefix = "maritime.openapi", name = "enable-trace-id-header", havingValue = "true")
    public OperationCustomizer traceIdHeaderCustomizer() {
        return (op, method) -> {
            op.addParametersItem(new HeaderParameter()
                    .name("X-Trace-ID")
                    .description("Distributed trace id. Auto-generated if absent.")
                    .required(false)
                    .schema(new StringSchema()));
            return op;
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "maritime.openapi", name = "enable-tenant-id-header", havingValue = "true")
    public OperationCustomizer tenantIdHeaderCustomizer() {
        return (op, method) -> {
            op.addParametersItem(new Parameter()
                    .name("X-Tenant-ID")
                    .in("header")
                    .description("Tenant identifier for multi-tenant routing.")
                    .required(false)
                    .schema(new StringSchema()));
            return op;
        };
    }
}
