package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPolicyServiceTest {

    @Test
    void defaultsMissingAuthModeToUserAuthRequired() {
        CapturingToolPolicyRepository repository = new CapturingToolPolicyRepository();
        ToolPolicyService service = new ToolPolicyService(repository);

        service.replacePolicies("agent-a", List.of(new ToolPolicyUpdate("tool-x", null)));

        assertThat(repository.saved).containsExactly(new ToolPolicy("agent-a", "tool-x", AuthMode.USER_AUTH_REQUIRED));
    }

    @Test
    void rejectsDuplicateToolIds() {
        ToolPolicyService service = new ToolPolicyService(new CapturingToolPolicyRepository());

        assertThatThrownBy(() -> service.replacePolicies("agent-a", List.of(
                new ToolPolicyUpdate("tool-x", AuthMode.NO_AUTH_REQUIRED),
                new ToolPolicyUpdate("tool-x", AuthMode.USER_AUTH_REQUIRED))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("duplicate toolId");
    }

    @Test
    void recordsAuditEventWhenReplacingPolicies() {
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        ToolPolicyService service = new ToolPolicyService(new CapturingToolPolicyRepository(), auditLogger);

        service.replacePolicies("agent-a", List.of(new ToolPolicyUpdate("tool-x", AuthMode.NO_AUTH_REQUIRED)));

        assertThat(auditLogger.events).hasSize(1);
        assertThat(auditLogger.events.getFirst()).containsEntry("eventType", "TOOL_POLICY_REPLACED");
        assertThat(auditLogger.events.getFirst()).containsEntry("agentId", "agent-a");
        assertThat(auditLogger.events.getFirst()).containsEntry("toolCount", "1");
    }

    private static final class CapturingToolPolicyRepository implements ToolPolicyRepository {
        private List<ToolPolicy> saved = new ArrayList<>();

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return Optional.empty();
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            return saved;
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            saved = new ArrayList<>(policies);
        }
    }

    private static final class CapturingAuditLogger implements AuditLogger {
        private final List<Map<String, String>> events = new ArrayList<>();

        @Override
        public void record(String eventType, Map<String, String> fields) {
            events.add(fields);
        }
    }
}
