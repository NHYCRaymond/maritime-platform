package com.maritime.platform.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(OpenApiProperties.class)
public class PlatformOpenApiAutoConfiguration {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI platformOpenApi(OpenApiProperties props) {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title(props.getTitle())
                        .description(props.getDescription())
                        .version(props.getVersion())
                        .contact(new Contact().name(props.getContactName())));

        if (props.isEnableBearerAuth()) {
            Components components = api.getComponents() == null
                    ? new Components() : api.getComponents();
            components.addSecuritySchemes(BEARER_AUTH_SCHEME,
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT Bearer token issued by iam-auth-service"));
            api.components(components);
            api.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
        }

        return api;
    }
}
