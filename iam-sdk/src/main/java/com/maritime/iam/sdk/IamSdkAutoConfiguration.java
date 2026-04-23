package com.maritime.iam.sdk;

import com.maritime.platform.common.mq.topology.IamTopologyConfiguration;
import com.maritime.platform.common.security.annotation.PublicApi;
import com.maritime.platform.common.security.annotation.RequirePermission;
import com.maritime.iam.sdk.annotation.BypassDataPermissionAspect;
import com.maritime.iam.sdk.client.HmacSignatureGenerator;
import com.maritime.iam.sdk.client.IamQueryClient;
import com.maritime.iam.sdk.dataperm.DataPermissionInjector;
import com.maritime.iam.sdk.dataperm.ScopeColumns;
import com.maritime.iam.sdk.event.IamEventListener;
import com.maritime.iam.sdk.filter.IamPermissionFilter;
import com.maritime.iam.sdk.mapper.ApiToPageMapper;
import com.maritime.iam.sdk.resource.NacosResourcePublisher;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Boot auto-configuration for iam-sdk.
 * Activated when {@code iam.center.url} is set.
 */
@Configuration
@EnableConfigurationProperties(IamSdkProperties.class)
@ConditionalOnProperty(prefix = "iam.center", name = "url")
public class IamSdkAutoConfiguration {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    IamSdkAutoConfiguration.class);

    @Bean
    RestTemplate iamSdkRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    HmacSignatureGenerator hmacSignatureGenerator(
            IamSdkProperties properties) {
        return new HmacSignatureGenerator(
                properties.getApp().getCode(),
                properties.getApp().getSecret());
    }

    @Bean
    IamQueryClient iamQueryClient(
            IamSdkProperties properties,
            HmacSignatureGenerator hmac) {
        return new IamQueryClient(
                iamSdkRestTemplate(),
                properties.getCenter().getUrl(),
                hmac);
    }

    @Bean
    ApiToPageMapper apiToPageMapper(
            IamQueryClient client,
            IamSdkProperties properties) {
        ApiToPageMapper mapper = new ApiToPageMapper(
                client, properties.getApp().getCode());
        mapper.refresh();
        return mapper;
    }

    @Bean
    @ConditionalOnClass(name =
            "org.springframework.web.servlet.mvc.method"
                    + ".annotation"
                    + ".RequestMappingHandlerMapping")
    FilterRegistrationBean<IamPermissionFilter>
            iamPermissionFilter(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping mapping,
            ApiToPageMapper pageMapper,
            IamQueryClient client,
            IamSdkProperties props) {
        IamPermissionFilter filter =
                new IamPermissionFilter(
                        mapping, pageMapper, client, props);
        FilterRegistrationBean<IamPermissionFilter> reg =
                new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }

    @Bean
    ScopeColumns iamSdkScopeColumns(IamSdkProperties properties) {
        IamSdkProperties.Scope scope = properties.getSdk().getScope();
        return new ScopeColumns(
                scope.getOrgColumn(),
                scope.getSelfColumn(),
                scope.getLineTypeColumn());
    }

    @Bean
    @ConditionalOnClass(name =
            "org.apache.ibatis.plugin.Interceptor")
    DataPermissionInjector dataPermissionInjector(
            ScopeColumns scopeColumns) {
        return new DataPermissionInjector(scopeColumns);
    }

    /**
     * Registers the AOP aspect that maps
     * {@link com.maritime.iam.sdk.annotation.BypassDataPermission}
     * to {@link com.maritime.iam.sdk.context.SystemContext}.
     * Conditional on AspectJ so the SDK still loads without
     * {@code spring-boot-starter-aop} on the classpath.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    BypassDataPermissionAspect bypassDataPermissionAspect() {
        return new BypassDataPermissionAspect();
    }

    @Bean
    @ConditionalOnClass(name =
            "org.springframework.web.servlet.mvc.method"
                    + ".annotation"
                    + ".RequestMappingHandlerMapping")
    ApplicationListener<ApplicationReadyEvent>
            iamEndpointSelfChecker(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping mapping) {
        return event -> checkEndpoints(mapping);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent>
            iamStartupWarnings(IamSdkProperties properties) {
        return event -> logStartupWarnings(properties);
    }

    private static void logStartupWarnings(
            IamSdkProperties properties) {
        if (properties.getSdk().isFailOpen()) {
            LOG.warn("[iam-sdk] Running in FAIL-OPEN mode. "
                    + "All requests permitted when IAM "
                    + "unavailable. DO NOT use in production.");
        }
        LOG.info("[iam-sdk] Initialized: center={}, app={}",
                properties.getCenter().getUrl(),
                properties.getApp().getCode());
    }

    private static void checkEndpoints(
            RequestMappingHandlerMapping mapping) {
        Map<RequestMappingInfo, HandlerMethod> methods =
                mapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod>
                entry : methods.entrySet()) {
            HandlerMethod hm = entry.getValue();
            if (!isRestControllerMethod(hm)) {
                continue;
            }
            if (hasPermAnnotation(hm)) {
                continue;
            }
            logUnannotated(entry.getKey(), hm);
        }
    }

    private static boolean isRestControllerMethod(
            HandlerMethod hm) {
        return AnnotatedElementUtils.hasAnnotation(
                hm.getBeanType(), RestController.class);
    }

    private static boolean hasPermAnnotation(
            HandlerMethod hm) {
        return AnnotatedElementUtils.findMergedAnnotation(
                hm.getMethod(),
                RequirePermission.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(
                hm.getMethod(),
                PublicApi.class) != null;
    }

    private static void logUnannotated(
            RequestMappingInfo info, HandlerMethod hm) {
        Set<String> patterns = info.getPatternValues();
        String path = patterns.isEmpty()
                ? "?" : patterns.iterator().next();
        String httpMethod = info.getMethodsCondition()
                .getMethods().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("?");
        LOG.warn("[iam-sdk] Unannotated endpoint found: "
                        + "{} {} in {}.{}"
                        + "() — will be auto-denied at runtime",
                httpMethod, path,
                hm.getBeanType().getSimpleName(),
                hm.getMethod().getName());
    }

    /**
     * Event listener configuration, conditional on
     * iam.event.enabled=true and AMQP on classpath.
     */
    @Configuration
    @ConditionalOnProperty(
            prefix = "iam.event",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnClass(name =
            "org.springframework.amqp.rabbit"
                    + ".annotation.RabbitListener")
    static class EventListenerConfiguration {

        @Bean
        Queue iamSdkCacheInvalidationQueue() {
            return new AnonymousQueue();
        }

        @Bean
        FanoutExchange iamCacheInvalidationExchange() {
            return new FanoutExchange(
                    IamTopologyConfiguration
                            .CACHE_INVALIDATION_EXCHANGE,
                    true, false);
        }

        @Bean
        Binding iamSdkCacheInvalidationBinding(
                Queue iamSdkCacheInvalidationQueue,
                FanoutExchange iamCacheInvalidationExchange) {
            return BindingBuilder
                    .bind(iamSdkCacheInvalidationQueue)
                    .to(iamCacheInvalidationExchange);
        }

        @Bean
        IamEventListener iamEventListener(
                StringRedisTemplate redisTemplate,
                ApiToPageMapper apiToPageMapper,
                IamSdkProperties properties) {
            return new IamEventListener(
                    redisTemplate,
                    apiToPageMapper,
                    properties.getApp().getCode());
        }
    }

}
