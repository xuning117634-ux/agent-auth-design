package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAuthorizationServiceTest {

    @Test
    void authorizesUserAuthRequiredToolForSevenDays() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED);
        FakeAuthorizationStore store = new FakeAuthorizationStore(false);
        ConversationAuthorizationService service = new ConversationAuthorizationService(policies, store, Duration.ofDays(7));

        service.authorize("agent-a:user-42:conversation-99", "tool-x");

        assertThat(store.authorizedTokenId).isEqualTo("agent-a:user-42:conversation-99");
        assertThat(store.authorizedToolId).isEqualTo("tool-x");
        assertThat(store.authorizedTtl).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void rejectsAuthorizationForNoAuthRequiredTool() {
        FakePolicyRepository policies = FakePolicyRepository.withPolicy(AuthMode.NO_AUTH_REQUIRED);
        ConversationAuthorizationService service =
                new ConversationAuthorizationService(policies, new FakeAuthorizationStore(false), Duration.ofDays(7));

        assertThatThrownBy(() -> service.authorize("agent-a:user-42:conversation-99", "tool-x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("authorization is not required");
    }

    @Test
    void rejectsAuthorizationForUnboundTool() {
        ConversationAuthorizationService service =
                new ConversationAuthorizationService(FakePolicyRepository.empty(), new FakeAuthorizationStore(false), Duration.ofDays(7));

        assertThatThrownBy(() -> service.authorize("agent-a:user-42:conversation-99", "tool-x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tool is not bound");
    }

    @Test
    void reportsAuthorizationStatusFromRedis() {
        ConversationAuthorizationService service =
                new ConversationAuthorizationService(FakePolicyRepository.empty(), new FakeAuthorizationStore(true), Duration.ofDays(7));

        AuthorizationStatus status = service.status("agent-a:user-42:conversation-99", "tool-x");

        assertThat(status).isEqualTo(AuthorizationStatus.AUTHORIZED);
    }

    @Test
    void recordsAuditEventsForAuthorizationStatusAndCleanup() {
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        ConversationAuthorizationService service = new ConversationAuthorizationService(
                FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED),
                new FakeAuthorizationStore(true),
                Duration.ofDays(7),
                auditLogger);

        service.authorize("agent-a:user-42:conversation-99", "tool-x");
        service.status("agent-a:user-42:conversation-99", "tool-x");
        service.cleanup("agent-a:user-42:conversation-99");

        assertThat(auditLogger.events).extracting(event -> event.get("eventType"))
                .containsExactly("CONVERSATION_AUTHORIZED", "AUTHORIZATION_STATUS_QUERIED", "CONVERSATION_AUTHORIZATION_CLEANED");
    }

    private static final class FakePolicyRepository implements ToolPolicyRepository {
        private final Optional<ToolPolicy> policy;

        private FakePolicyRepository(Optional<ToolPolicy> policy) {
            this.policy = policy;
        }

        static FakePolicyRepository empty() {
            return new FakePolicyRepository(Optional.empty());
        }

        static FakePolicyRepository withPolicy(AuthMode authMode) {
            return new FakePolicyRepository(Optional.of(new ToolPolicy("agent-a", "tool-x", authMode)));
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
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
        private String authorizedTokenId;
        private String authorizedToolId;
        private Duration authorizedTtl;

        private FakeAuthorizationStore(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean exists(String tokenId, String toolId) {
            return exists;
        }

        @Override
        public void authorize(String tokenId, String toolId, Duration ttl) {
            this.authorizedTokenId = tokenId;
            this.authorizedToolId = toolId;
            this.authorizedTtl = ttl;
        }

        @Override
        public long cleanup(String tokenId) {
            return 1;
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
