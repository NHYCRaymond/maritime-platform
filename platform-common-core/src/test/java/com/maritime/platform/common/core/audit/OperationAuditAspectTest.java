package com.maritime.platform.common.core.audit;

import com.maritime.platform.common.core.annotation.OperationAudit;
import com.maritime.platform.common.core.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = OperationAuditAspectTest.TestConfig.class)
class OperationAuditAspectTest {

    @Autowired
    private AuditEventPublisher publisher;

    @Autowired
    private SampleService sampleService;

    @BeforeEach
    void resetMock() {
        reset(publisher);
    }

    @Test
    void publishesEventAfterSuccessfulMethod() {
        sampleService.simpleOperation("USER001");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher, times(1)).publish(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.operationType()).isEqualTo("create_user");
        assertThat(event.targetType()).isEqualTo("USER");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void doesNotPublishWhenMethodThrows() {
        assertThatThrownBy(() -> sampleService.failingOperation("ROLE001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated failure");

        verify(publisher, never()).publish(any());
    }

    @Test
    void resolvesSpelTargetCode() {
        GrantRoleCommand cmd = new GrantRoleCommand("ADMIN");
        sampleService.grantRole(cmd);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher, times(1)).publish(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.operationType()).isEqualTo("grant_role");
        assertThat(event.targetType()).isEqualTo("ROLE");
        assertThat(event.targetCode()).isEqualTo("ADMIN");
    }

    // ---------- test helpers ----------

    record GrantRoleCommand(String roleCode) {}

    static class SampleService {

        @OperationAudit(operationType = "create_user", targetType = "USER")
        public void simpleOperation(String userId) {
            // success
        }

        @OperationAudit(operationType = "delete_role", targetType = "ROLE")
        public void failingOperation(String roleId) {
            throw new RuntimeException("simulated failure");
        }

        @OperationAudit(
                operationType = "grant_role",
                targetType = "ROLE",
                targetCodeExpr = "#cmd.roleCode"
        )
        public void grantRole(GrantRoleCommand cmd) {
            // success
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public AuditEventPublisher auditEventPublisher() {
            return mock(AuditEventPublisher.class);
        }

        @Bean
        public AuditOperatorResolver auditOperatorResolver() {
            return new DefaultAuditOperatorResolver();
        }

        @Bean
        public OperationAuditAspect operationAuditAspect(AuditEventPublisher publisher,
                                                         AuditOperatorResolver resolver) {
            return new OperationAuditAspect(publisher, resolver);
        }

        @Bean
        public SampleService sampleService() {
            return new SampleService();
        }
    }
}