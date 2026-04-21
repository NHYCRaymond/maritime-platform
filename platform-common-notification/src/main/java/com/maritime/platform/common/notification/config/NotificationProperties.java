package com.maritime.platform.common.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the notification subsystem.
 */
@ConfigurationProperties(prefix = "platform.notification")
public class NotificationProperties {

    /** Enable notification dispatching. Default true; set false to disable auto-config. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}