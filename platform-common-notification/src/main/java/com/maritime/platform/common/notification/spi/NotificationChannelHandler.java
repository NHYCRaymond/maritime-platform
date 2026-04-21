package com.maritime.platform.common.notification.spi;

import com.maritime.platform.common.notification.api.Channel;
import com.maritime.platform.common.notification.api.NotificationRequest;

/**
 * SPI implemented by adapters that deliver notifications on a specific {@link Channel}.
 *
 * <p>Each channel must have at most one handler registered; the dispatcher will reject
 * duplicates at construction time.</p>
 */
public interface NotificationChannelHandler {

    /** Which channel this handler serves. */
    Channel channel();

    /**
     * Handle the notification for this channel.
     * Filter {@code request.recipients()} to those addressable via this channel.
     * Throw {@link RuntimeException} on unrecoverable errors.
     *
     * @param request the notification request
     */
    void handle(NotificationRequest request);
}
