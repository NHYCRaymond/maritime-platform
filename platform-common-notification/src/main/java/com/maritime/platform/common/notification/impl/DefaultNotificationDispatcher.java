package com.maritime.platform.common.notification.impl;

import com.maritime.platform.common.notification.api.Channel;
import com.maritime.platform.common.notification.api.DispatchResult;
import com.maritime.platform.common.notification.api.NotificationDispatcher;
import com.maritime.platform.common.notification.api.NotificationRequest;
import com.maritime.platform.common.notification.spi.NotificationChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default fan-out dispatcher that iterates the request's channels and delegates to
 * the matching {@link NotificationChannelHandler}. Missing handlers and per-handler
 * exceptions are aggregated into a single {@link DispatchResult.Failed} outcome.
 *
 * <p>This implementation does not itself de-duplicate by idempotency key; adapters
 * that require persistent de-dup should wrap or replace this bean.</p>
 */
public class DefaultNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationDispatcher.class);

    private final Map<Channel, NotificationChannelHandler> handlers;

    public DefaultNotificationDispatcher(List<NotificationChannelHandler> handlerList) {
        Objects.requireNonNull(handlerList, "handlerList cannot be null");
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(
                        NotificationChannelHandler::channel,
                        h -> h,
                        (existing, replacement) -> {
                            throw new IllegalStateException(
                                    "Duplicate handler for channel " + existing.channel());
                        }
                ));
    }

    @Override
    public DispatchResult dispatch(NotificationRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        List<String> failedChannels = new ArrayList<>();
        String lastFailure = null;

        for (Channel channel : request.channels()) {
            NotificationChannelHandler handler = handlers.get(channel);
            if (handler == null) {
                log.warn("No handler registered for channel {}, skipping (tenant={}, correlationId={})",
                        channel, request.tenantId(), request.correlationId());
                failedChannels.add(channel.name());
                lastFailure = "no handler for " + channel;
                continue;
            }
            try {
                handler.handle(request);
            } catch (RuntimeException ex) {
                log.error("Channel {} handler failed for request (tenant={}, correlationId={}): {}",
                        channel, request.tenantId(), request.correlationId(), ex.getMessage(), ex);
                failedChannels.add(channel.name());
                lastFailure = ex.getMessage();
            }
        }

        if (failedChannels.isEmpty()) {
            String dispatchId = request.idempotencyKey() != null
                    ? request.idempotencyKey()
                    : UUID.randomUUID().toString();
            return new DispatchResult.Success(dispatchId);
        }
        return new DispatchResult.Failed(
                "Dispatch partially/fully failed on channels: " + failedChannels + "; last error: " + lastFailure,
                null
        );
    }
}
