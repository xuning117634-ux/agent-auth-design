package com.huawei.it.roma.liveeda.policycenter.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.Decision;
import com.huawei.it.roma.liveeda.policycenter.domain.DecisionReason;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    void requestsPerCallAuthorizationWhenGrantIsMissing() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.PER_CALL_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.AUTHORIZATION_REQUIRED);
        assertThat(decision.reason()).isEqualTo(DecisionReason.PER_CALL_AUTHORIZATION_REQUIRED);
        assertThat(grants.consumeCalls).isOne();
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void consumesPerCallAuthorizationWhenGrantExists() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.PER_CALL_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        grants.consumeResult = true;
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision firstDecision = service.decide("agent-a:user-42:conversation-99", "tool-x");
        grants.consumeResult = false;
        AuthorizationDecision secondDecision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(firstDecision.decision()).isEqualTo(Decision.ALLOW);
        assertThat(firstDecision.reason()).isEqualTo(DecisionReason.PER_CALL_AUTHORIZED);
        assertThat(secondDecision.decision()).isEqualTo(Decision.AUTHORIZATION_REQUIRED);
        assertThat(secondDecision.reason()).isEqualTo(DecisionReason.PER_CALL_AUTHORIZATION_REQUIRED);
        assertThat(grants.consumeCalls).isEqualTo(2);
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void skipsToolUserPolicyWhenEvaluatorAllowsTool() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(
                policies,
                grants,
                (agentId, toolId, userId) -> true,
                NoopAuditLogger.INSTANCE);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.ALLOW);
        assertThat(decision.reason()).isEqualTo(DecisionReason.NO_AUTH_REQUIRED);
    }

    @Test
    void deniesWhenToolUserPolicyRejectsUser() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(
                policies,
                grants,
                (agentId, toolId, userId) -> false,
                NoopAuditLogger.INSTANCE);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.USER_TOOL_ACCESS_DENIED);
        assertThat(grants.existsCalls).isZero();
    }

    @Test
    void toolUserPolicyAllowsUserBeforeRedisAuthorizationCheck() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(
                policies,
                grants,
                (agentId, toolId, userId) -> true,
                NoopAuditLogger.INSTANCE);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.AUTHORIZATION_REQUIRED);
        assertThat(decision.reason()).isEqualTo(DecisionReason.USER_AUTHORIZATION_REQUIRED);
        assertThat(grants.existsCalls).isOne();
    }

    @Test
    void toolUserPolicyFailureFailsClosedAsPolicyStoreUnavailable() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        FakeAuthorizationStore grants = new FakeAuthorizationStore(false);
        AuthorizationDecisionService service = new AuthorizationDecisionService(
                policies,
                grants,
                (agentId, toolId, userId) -> {
                    throw new IllegalStateException("database unavailable");
                },
                NoopAuditLogger.INSTANCE);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.POLICY_STORE_UNAVAILABLE);
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
    void logsOriginalExceptionWhenPolicyStoreFailsClosed() {
        FakePolicyRepository policies = FakePolicyRepository.failing();
        FakeAuthorizationStore grants = new FakeAuthorizationStore(true);
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(
                    "AUTHORIZATION_DECISION_FAIL_CLOSED",
                    "tokenId=agent-a:user-42:conversation-99",
                    "agentId=agent-a",
                    "toolId=tool-x",
                    "reason=POLICY_STORE_UNAVAILABLE");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
        });
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
    void perCallConsumeFailureFailsClosed() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.PER_CALL_AUTH_REQUIRED);
        FakeAuthorizationStore grants = FakeAuthorizationStore.failing();
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);

        AuthorizationDecision decision = service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.reason()).isEqualTo(DecisionReason.AUTHORIZATION_STORE_UNAVAILABLE);
    }

    @Test
    void logsOriginalExceptionWhenAuthorizationStoreFailsClosed() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore grants = FakeAuthorizationStore.failing();
        AuthorizationDecisionService service = new AuthorizationDecisionService(policies, grants);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        service.decide("agent-a:user-42:conversation-99", "tool-x");

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(
                    "AUTHORIZATION_DECISION_FAIL_CLOSED",
                    "tokenId=agent-a:user-42:conversation-99",
                    "agentId=agent-a",
                    "toolId=tool-x",
                    "reason=AUTHORIZATION_STORE_UNAVAILABLE");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("redis unavailable");
        });
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
        private int consumeCalls;
        private boolean consumeResult;

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
        public boolean consume(String tokenId, String toolId) {
            consumeCalls++;
            if (fail) {
                throw new IllegalStateException("redis unavailable");
            }
            return consumeResult;
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

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthorizationDecisionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
