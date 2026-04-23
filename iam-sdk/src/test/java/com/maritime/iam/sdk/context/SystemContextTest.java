package com.maritime.iam.sdk.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemContextTest {

    @AfterEach
    void cleanup() {
        // Paranoia: prevent leakage if a test throws mid-scope.
        while (SystemContext.isActive()) {
            SystemContext.exit();
        }
    }

    @Test
    void inactive_by_default() {
        assertThat(SystemContext.isActive()).isFalse();
    }

    @Test
    void enter_makes_active_exit_deactivates() {
        SystemContext.enter();
        assertThat(SystemContext.isActive()).isTrue();

        SystemContext.exit();
        assertThat(SystemContext.isActive()).isFalse();
    }

    @Test
    void nested_enters_require_matching_exits() {
        SystemContext.enter();
        SystemContext.enter();
        SystemContext.enter();

        assertThat(SystemContext.isActive()).isTrue();

        SystemContext.exit();
        assertThat(SystemContext.isActive()).isTrue();

        SystemContext.exit();
        assertThat(SystemContext.isActive()).isTrue();

        SystemContext.exit();
        assertThat(SystemContext.isActive()).isFalse();
    }

    @Test
    void extra_exit_is_tolerated() {
        SystemContext.exit();   // no-op on clean state
        assertThat(SystemContext.isActive()).isFalse();

        SystemContext.enter();
        SystemContext.exit();
        SystemContext.exit();   // extra — still safe
        assertThat(SystemContext.isActive()).isFalse();
    }

    @Test
    void scoped_auto_closes() throws Exception {
        assertThat(SystemContext.isActive()).isFalse();
        try (AutoCloseable ignored = SystemContext.scoped()) {
            assertThat(SystemContext.isActive()).isTrue();
        }
        assertThat(SystemContext.isActive()).isFalse();
    }

    @Test
    void threads_have_independent_state() throws Exception {
        SystemContext.enter();
        boolean[] otherThreadActive = {true};

        Thread t = new Thread(() ->
                otherThreadActive[0] = SystemContext.isActive());
        t.start();
        t.join();

        assertThat(otherThreadActive[0])
                .as("other thread must not see this thread's flag")
                .isFalse();
        assertThat(SystemContext.isActive()).isTrue();
    }
}
