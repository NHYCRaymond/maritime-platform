package com.maritime.platform.common.redis.leader;

import com.maritime.platform.common.redis.lockport.LockPort;
import com.maritime.platform.common.redis.lockport.LockPort.LockHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LeaderElectedAspect}. Uses Mockito to stub {@link LockPort}
 * — no Redis required.
 */
class LeaderElectedAspectTest {

    private LockPort lockPort;
    private LeaderElectedAspect aspect;

    @BeforeEach
    void setUp() {
        lockPort = mock(LockPort.class);
        aspect = new LeaderElectedAspect(lockPort);
    }

    static class ScanService {
        final AtomicInteger voidCalls = new AtomicInteger();
        final AtomicInteger returningCalls = new AtomicInteger();

        @LeaderElected(name = "scan-job")
        public void runVoid() {
            voidCalls.incrementAndGet();
        }

        @LeaderElected(name = "lookup-job", silentSkip = false)
        public String lookup() {
            returningCalls.incrementAndGet();
            return "result";
        }

        @LeaderElected(name = "configurable", waitMillis = 1000, leaseMillis = 5000)
        public void runConfigurable() {
            voidCalls.incrementAndGet();
        }
    }

    private ScanService proxyOf(ScanService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private LockHandle fakeHandle() {
        return new LockHandle() {
            @Override public String lockKey() { return "pe:lock:leader:x"; }
            @Override public void unlock() { }
            @Override public void close() { }
        };
    }

    @Test
    void voidMethod_whenAcquiresLock_executesBody() {
        when(lockPort.tryLock(eq("leader"), eq("scan-job"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(fakeHandle()));

        ScanService target = new ScanService();
        proxyOf(target).runVoid();

        assertThat(target.voidCalls.get()).isEqualTo(1);
        verify(lockPort, times(1)).tryLock(eq("leader"), eq("scan-job"),
                any(Duration.class), any(Duration.class));
    }

    @Test
    void voidMethod_whenLockHeld_skipsBody() {
        when(lockPort.tryLock(eq("leader"), eq("scan-job"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        ScanService target = new ScanService();
        proxyOf(target).runVoid();

        assertThat(target.voidCalls.get()).isZero();
    }

    @Test
    void methodWithReturn_whenSkipped_returnsNull() {
        when(lockPort.tryLock(eq("leader"), eq("lookup-job"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        ScanService target = new ScanService();
        String result = proxyOf(target).lookup();

        assertThat(result).isNull();
        assertThat(target.returningCalls.get()).isZero();
    }

    @Test
    void methodWithReturn_whenAcquired_returnsResult() {
        when(lockPort.tryLock(eq("leader"), eq("lookup-job"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(fakeHandle()));

        ScanService target = new ScanService();
        String result = proxyOf(target).lookup();

        assertThat(result).isEqualTo("result");
        assertThat(target.returningCalls.get()).isEqualTo(1);
    }

    @Test
    void annotationAttributes_arePassedToLockPort() {
        when(lockPort.tryLock(eq("leader"), eq("configurable"),
                eq(Duration.ofMillis(1000)), eq(Duration.ofMillis(5000))))
                .thenReturn(Optional.of(fakeHandle()));

        ScanService target = new ScanService();
        proxyOf(target).runConfigurable();

        assertThat(target.voidCalls.get()).isEqualTo(1);
        verify(lockPort, times(1)).tryLock(eq("leader"), eq("configurable"),
                eq(Duration.ofMillis(1000)), eq(Duration.ofMillis(5000)));
    }

    @Test
    void lockReleased_viaHandleClose() {
        LockHandle handle = mock(LockHandle.class);
        when(lockPort.tryLock(eq("leader"), eq("scan-job"), any(Duration.class), any(Duration.class)))
                .thenReturn(Optional.of(handle));

        proxyOf(new ScanService()).runVoid();

        verify(handle, times(1)).close();
        verify(handle, never()).unlock();
    }
}