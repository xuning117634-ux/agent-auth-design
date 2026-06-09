package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.Decision;
import com.huawei.it.roma.liveeda.policycenter.domain.DecisionReason;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDecisionServiceTest {

    @Test
    void deniesUnboundToolWithoutQueryingRedis() {
        FakePolicyRepository policies = FakePolicyRepository.empty();
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.TOOL_NOT_BOUND);
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void allowsNoAuthRequiredToolWithoutQueryingRedis() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.ALLOW);
        assertThat(decision.reason()).isEqualTo(DecisionReason.NO_AUTH_REQUIRED);
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void allowsUserAuthRequiredToolWhenConversationGrantExists() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(true);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.ALLOW);
        assertThat(decision.reason()).isEqualTo(DecisionReason.CONVERSATION_AUTHORIZED);
    }

    @Test
    void requestsAuthorizationWhenConversationGrantIsMissing() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.AUTHORIZATION_REQUIRED);
        assertThat(decision.reason()).isEqualTo(DecisionReason.USER_AUTHORIZATION_REQUIRED);
    }

    @Test
    void invalidTokenIdReturnsDenyWithoutQueryingStores() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(true);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.INVALID_TOKEN_ID);
        assertThat(policies.findCalls).isZero();
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void databaseFailureFailsClosed() {
        FakePolicyRepository policies = FakePolicyRepository.failing();
        FakeAuthorizationStore grants = new FakeAuthorizationStore(true);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.POLICY_STORE_UNAVAILABLE);
    }

    @Test
    void redisFailureFailsClosed() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore grants = FakeAuthorizationStore.failing();
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE);
    }

    @Test
    void recordsAuditEventForDecision() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants, auditLogger);

        service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(auditLogger.events).hasSize(1);
        assertThat(auditLogger.events.getFirst()).containsEntry("eventType", "AUTHORIZATION_DECISION");
        assertThat(auditLogger.events.getFirst()).containsEntry("tokenId", "agent-a:user-42:conversation-99");
        assertThat(auditLogger.events.getFirst()).containsEntry("agentId", "agent-a");
        assertThat(auditLogger.events.getFirst()).containsEntry("toolId", "tool-x");
        assertThat(auditLogger.events.getFirst()).containsEntry("decision", "ALLOW");
        assertThat(auditLogger.events.getFirst()).containsEntry("reason", "NO_AUTH_REQUIRED");
    }

    private static final class FakePolicyRepository implements ToolPolicyRepository {
        private final Optional<ToolPolicy> policy;
        private final boolean fail;
        private int findCalls;

        private FakePolicyRepository(Optional<ToolPolicy> policy, boolean fail) {
            this.policy = policy;
            this.fail = fail;
        }

        static FakePolicyRepository empty() {
            return new FakePolicyRepository(Optional.empty(), false);
        }

        static FakePolicyRepository withPolicy(AuthMode authMode) {
            return new FakePolicyRepository(Optional.of(new ToolPolicy("agent-a", "tool-x", authMode)), false);
        }

        static FakePolicyRepository failing() {
            return new FakePolicyRepository(Optional.empty(), true);
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            findCalls++;
            if (fail) {
                throw new IllegalStateException("database unavailable");
            }
            return policy;
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeAuthorizationStore implements ConversationAuthorizationStore {
        private final boolean exists;
        private final boolean fail;
        private int existsCalls;

        FakeAuthorizationStore(boolean exists) {
            this(exists, false);
        }

        private FakeAuthorizationStore(boolean exists, boolean fail) {
            this.exists = exists;
            this.fail = fail;
        }

        static FakeAuthorizationStore failing() {
            return new FakeAuthorizationStore(false, true);
        }

        @Override
        public boolean exists(String tokenId, String toolId) {
            existsCalls++;
            if (fail) {
                throw new IllegalStateException("redis unavailable");
            }
            return exists;
        }

        @Override
        public void authorize(String tokenId, String toolId, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long cleanup(String tokenId) {
            throw new UnsupportedOperationException();
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
