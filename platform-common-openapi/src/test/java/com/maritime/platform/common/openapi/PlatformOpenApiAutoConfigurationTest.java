package com.maritime.platform.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOpenApiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformOpenApiAutoConfiguration.class));

    @Test
    void registersOpenApiBeanWithTitleAndBearerAuth() {
        runner.withPropertyValues(
                "maritime.openapi.title=Test API",
                "maritime.openapi.description=An API",
                "maritime.openapi.version=2.0.0"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenAPI.class);
            OpenAPI api = ctx.getBean(OpenAPI.class);
            assertThat(api.getInfo().getTitle()).isEqualTo("Test API");
            assertThat(api.getInfo().getVersion()).isEqualTo("2.0.0");
            assertThat(api.getComponents().getSecuritySchemes())
                    .containsKey("bearerAuth");
        });
    }

    @Test
    void omitsBearerAuthWhenDisabled() {
        runner.withPropertyValues("maritime.openapi.enable-bearer-auth=false").run(ctx -> {
            OpenAPI api = ctx.getBean(OpenAPI.class);
            assertThat(api.getSecurity()).isNullOrEmpty();
        });
    }

    @Test
    void defaultsAppliedWhenNoPropertiesSet() {
        runner.run(ctx -> {
            OpenAPI api = ctx.getBean(OpenAPI.class);
            assertThat(api.getInfo().getTitle()).isEqualTo("API Documentation");
            assertThat(api.getInfo().getContact().getName()).isEqualTo("Maritime Platform");
        });
    }
}
