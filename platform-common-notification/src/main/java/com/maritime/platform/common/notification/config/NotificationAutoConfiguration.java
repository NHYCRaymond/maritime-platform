package com.maritime.platform.common.notification.config;

import com.maritime.platform.common.notification.api.NotificationDispatcher;
import com.maritime.platform.common.notification.impl.DefaultNotificationDispatcher;
import com.maritime.platform.common.notification.spi.NotificationChannelHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

/**
 * Registers a {@link DefaultNotificationDispatcher} wired with every
 * {@link NotificationChannelHandler} bean in the application context.
 *
 * <p>Consumers can override the dispatcher by declaring their own
 * {@code @Bean NotificationDispatcher}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationDispatcher notificationDispatcher(List<NotificationChannelHandler> handlers) {
        return new DefaultNotificationDispatcher(handlers != null ? handlers : Collections.emptyList());
    }
}