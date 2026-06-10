package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
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
import static org.assertj.core.api.Assertions.catchThrowable;
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

    @Test
    void listPoliciesPreservesStoreFailureCauseAndContext() {
        ToolPolicyService service = new ToolPolicyService(FailingToolPolicyRepository.listFailure());

        Throwable thrown = catchThrowable(() -> service.listPolicies("agent-a"));

        assertStoreFailure(thrown, "database list unavailable", "LIST_TOOL_POLICIES");
    }

    @Test
    void replacePoliciesPreservesStoreFailureCauseAndContext() {
        ToolPolicyService service = new ToolPolicyService(FailingToolPolicyRepository.replaceFailure());

        Throwable thrown = catchThrowable(() -> service.replacePolicies(
                "agent-a",
                List.of(new ToolPolicyUpdate("tool-x", AuthMode.USER_AUTH_REQUIRED))));

        assertStoreFailure(thrown, "database replace unavailable", "REPLACE_TOOL_POLICIES");
    }

    private void assertStoreFailure(Throwable thrown, String causeMessage, String operation) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException exception = (ApiException) thrown;
        assertThat(exception.code()).isEqualTo(ErrorCode.POLICY_STORE_UNAVAILABLE);
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessage(causeMessage);
        assertThat(exception.context()).containsEntry("operation", operation)
                .containsEntry("agentId", "agent-a");
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

    private static final class FailingToolPolicyRepository implements ToolPolicyRepository {
        private final boolean failList;
        private final boolean failReplace;

        private FailingToolPolicyRepository(boolean failList, boolean failReplace) {
            this.failList = failList;
            this.failReplace = failReplace;
        }

        static FailingToolPolicyRepository listFailure() {
            return new FailingToolPolicyRepository(true, false);
        }

        static FailingToolPolicyRepository replaceFailure() {
            return new FailingToolPolicyRepository(false, true);
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return Optional.empty();
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            if (failList) {
                throw new IllegalStateException("database list unavailable");
            }
            return List.of();
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            if (failReplace) {
                throw new IllegalStateException("database replace unavailable");
            }
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
