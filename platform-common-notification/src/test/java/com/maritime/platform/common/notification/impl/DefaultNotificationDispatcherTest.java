package com.maritime.platform.common.notification.impl;

import com.maritime.platform.common.notification.api.Channel;
import com.maritime.platform.common.notification.api.DispatchResult;
import com.maritime.platform.common.notification.api.NotificationRequest;
import com.maritime.platform.common.notification.spi.NotificationChannelHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultNotificationDispatcherTest {

    private NotificationRequest requestWithChannels(Set<Channel> channels) {
        return new NotificationRequest(
                "TEMPLATE_A",
                channels,
                List.of("user-1"),
                null,
                "tenant-1",
                "corr-1",
                null
        );
    }

    /**
     * Simple capturing handler used instead of Mockito for clarity and to avoid
     * mocking final methods on records.
     */
    private static final class CapturingHandler implements NotificationChannelHandler {
        private final Channel channel;
        private final AtomicInteger invocations = new AtomicInteger();
        private final RuntimeException errorToThrow;

        CapturingHandler(Channel channel) { this(channel, null); }
        CapturingHandler(Channel channel, RuntimeException errorToThrow) {
            this.channel = channel;
            this.errorToThrow = errorToThrow;
        }
        @Override public Channel channel() { return channel; }
        @Override public void handle(NotificationRequest request) {
            invocations.incrementAndGet();
            if (errorToThrow != null) {
                throw errorToThrow;
            }
        }
        int invocations() { return invocations.get(); }
    }

    @Test
    void dispatch_withAllHandlersSuccess_returnsSuccess() {
        CapturingHandler station = new CapturingHandler(Channel.STATION);
        CapturingHandler email = new CapturingHandler(Channel.EMAIL);
        DefaultNotificationDispatcher dispatcher =
                new DefaultNotificationDispatcher(List.of(station, email));

        DispatchResult result = dispatcher.dispatch(
                requestWithChannels(Set.of(Channel.STATION, Channel.EMAIL)));

        assertThat(result).isInstanceOf(DispatchResult.Success.class);
        DispatchResult.Success success = (DispatchResult.Success) result;
        assertThat(success.dispatchId()).isNotBlank();
        assertThat(station.invocations()).isEqualTo(1);
        assertThat(email.invocations()).isEqualTo(1);
    }

    @Test
    void dispatch_withHandlerThrows_returnsFailed() {
        CapturingHandler okHandler = new CapturingHandler(Channel.STATION);
        CapturingHandler badHandler = new CapturingHandler(
                Channel.EMAIL, new RuntimeException("smtp down"));
        DefaultNotificationDispatcher dispatcher =
                new DefaultNotificationDispatcher(List.of(okHandler, badHandler));

        DispatchResult result = dispatcher.dispatch(
                requestWithChannels(Set.of(Channel.STATION, Channel.EMAIL)));

        assertThat(result).isInstanceOf(DispatchResult.Failed.class);
        DispatchResult.Failed failed = (DispatchResult.Failed) result;
        assertThat(failed.reason()).contains("EMAIL").contains("smtp down");
        // The non-failing handler should still have been invoked.
        assertThat(okHandler.invocations()).isEqualTo(1);
        assertThat(badHandler.invocations()).isEqualTo(1);
    }

    @Test
    void dispatch_withNoHandlerForChannel_returnsFailed() {
        CapturingHandler station = new CapturingHandler(Channel.STATION);
        DefaultNotificationDispatcher dispatcher =
                new DefaultNotificationDispatcher(List.of(station));

        DispatchResult result = dispatcher.dispatch(
                requestWithChannels(Set.of(Channel.STATION, Channel.SMS)));

        assertThat(result).isInstanceOf(DispatchResult.Failed.class);
        DispatchResult.Failed failed = (DispatchResult.Failed) result;
        assertThat(failed.reason()).contains("SMS").contains("no handler for SMS");
        assertThat(station.invocations()).isEqualTo(1);
    }

    @Test
    void dispatch_withNullRequest_throwsNPE() {
        DefaultNotificationDispatcher dispatcher =
                new DefaultNotificationDispatcher(new ArrayList<>());

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    void constructor_withDuplicateChannelHandlers_throwsIllegalState() {
        CapturingHandler h1 = new CapturingHandler(Channel.STATION);
        CapturingHandler h2 = new CapturingHandler(Channel.STATION);

        assertThatThrownBy(() -> new DefaultNotificationDispatcher(List.of(h1, h2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate handler for channel STATION");
    }

    @Test
    void dispatch_withIdempotencyKey_returnsKeyAsDispatchId() {
        CapturingHandler station = new CapturingHandler(Channel.STATION);
        DefaultNotificationDispatcher dispatcher =
                new DefaultNotificationDispatcher(List.of(station));

        NotificationRequest request = new NotificationRequest(
                "TEMPLATE_A",
                Set.of(Channel.STATION),
                List.of("user-1"),
                null,
                "tenant-1",
                "corr-1",
                "idem-123"
        );

        DispatchResult result = dispatcher.dispatch(request);
        assertThat(result).isInstanceOf(DispatchResult.Success.class);
        assertThat(((DispatchResult.Success) result).dispatchId()).isEqualTo("idem-123");
    }

    @Test
    void constructor_withEmptyHandlerList_isAllowed() {
        assertThatCode(() -> new DefaultNotificationDispatcher(new ArrayList<>()))
                .doesNotThrowAnyException();
    }
}
