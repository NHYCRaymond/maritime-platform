package com.maritime.platform.common.outbox;

import com.maritime.platform.common.outbox.dataobject.OutboxEntryDO;
import com.maritime.platform.common.outbox.model.OutboxEntryStatus;
import com.maritime.platform.common.outbox.poller.OutboxPoller;
import com.maritime.platform.common.outbox.poller.OutboxProperties;
import com.maritime.platform.common.outbox.spi.OutboxEventPublisher;
import com.maritime.platform.common.outbox.store.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxStore store;

    @Mock
    private OutboxEventPublisher publisher;

    private OutboxProperties props;
    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        props = new OutboxProperties();
        poller = new OutboxPoller(store, publisher, props);
    }

    private OutboxEntryDO entry(long id, int retryCount) {
        OutboxEntryDO e = new OutboxEntryDO();
        e.setId(id);
        e.setAggregateType("Order");
        e.setAggregateId("ORD-001");
        e.setEventType("OrderCreated");
        e.setPayload("{\"id\":\"ORD-001\"}");
        e.setStatus(OutboxEntryStatus.PENDING.name());
        e.setRetryCount(retryCount);
        return e;
    }

    @Test
    void publishesDueEntriesAndMarksPublished() throws Exception {
        OutboxEntryDO e1 = entry(1L, 0);
        OutboxEntryDO e2 = entry(2L, 0);
        when(store.findDue(props.getBatchSize())).thenReturn(List.of(e1, e2));

        poller.poll();

        verify(publisher).publish(e1);
        verify(publisher).publish(e2);
        verify(store).markPublished(1L);
        verify(store).markPublished(2L);
        verify(store, never()).markFailed(anyLong(), anyInt(), any(), any());
    }

    @Test
    void marksFailedWithBackoffOnPublisherException() throws Exception {
        OutboxEntryDO e = entry(10L, 0);
        when(store.findDue(props.getBatchSize())).thenReturn(List.of(e));
        doThrow(new RuntimeException("broker unavailable")).when(publisher).publish(e);

        poller.poll();

        ArgumentCaptor<Integer> retryCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

        verify(store).markFailed(eq(10L), retryCaptor.capture(),
                nextRetryCaptor.capture(), errorCaptor.capture());
        verify(store, never()).markPublished(any());

        assertThat(retryCaptor.getValue()).isEqualTo(1);
        assertThat(nextRetryCaptor.getValue()).isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(errorCaptor.getValue()).contains("broker unavailable");
    }

    @Test
    void givesUpAfterMaxRetries() throws Exception {
        // Entry already at maxRetries — next attempt should exhaust retries
        int maxRetries = props.getMaxRetries();
        OutboxEntryDO e = entry(99L, maxRetries);
        when(store.findDue(props.getBatchSize())).thenReturn(List.of(e));
        doThrow(new RuntimeException("still broken")).when(publisher).publish(e);

        poller.poll();

        // nextRetryAt must be null (give up permanently)
        verify(store).markFailed(eq(99L), eq(maxRetries + 1),
                isNull(), eq("still broken"));
        verify(store, never()).markPublished(any());
    }
}