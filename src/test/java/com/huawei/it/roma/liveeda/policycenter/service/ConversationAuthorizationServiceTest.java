package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
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
import static org.assertj.core.api.Assertions.catchThrowable;
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

    @Test
    void authorizationPolicyFailurePreservesCauseAndContext() {
        ConversationAuthorizationService service = new ConversationAuthorizationService(
                FakePolicyRepository.failing(),
                new FakeAuthorizationStore(false),
                Duration.ofDays(7));

        Throwable thrown = catchThrowable(() -> service.authorize("agent-a:user-42:conversation-99", "tool-x"));

        assertPolicyStoreFailure(thrown, "policy db unavailable");
    }

    @Test
    void authorizationWriteFailurePreservesCauseAndContext() {
        ConversationAuthorizationService service = new ConversationAuthorizationService(
                FakePolicyRepository.withPolicy(AuthMode.USER_AUTH_REQUIRED),
                FakeAuthorizationStore.failingAuthorize(),
                Duration.ofDays(7));

        Throwable thrown = catchThrowable(() -> service.authorize("agent-a:user-42:conversation-99", "tool-x"));

        assertAuthorizationStoreFailure(thrown, "redis authorize unavailable", "AUTHORIZE_CONVERSATION_TOOL");
    }

    @Test
    void statusFailurePreservesCauseAndContext() {
        ConversationAuthorizationService service = new ConversationAuthorizationService(
                FakePolicyRepository.empty(),
                FakeAuthorizationStore.failingExists(),
                Duration.ofDays(7));

        Throwable thrown = catchThrowable(() -> service.status("agent-a:user-42:conversation-99", "tool-x"));

        assertAuthorizationStoreFailure(thrown, "redis exists unavailable", "QUERY_CONVERSATION_AUTHORIZATION_STATUS");
    }

    @Test
    void cleanupFailurePreservesCauseAndContext() {
        ConversationAuthorizationService service = new ConversationAuthorizationService(
                FakePolicyRepository.empty(),
                FakeAuthorizationStore.failingCleanup(),
                Duration.ofDays(7));

        Throwable thrown = catchThrowable(() -> service.cleanup("agent-a:user-42:conversation-99"));

        assertAuthorizationStoreFailure(thrown, "redis cleanup unavailable", "CLEANUP_CONVERSATION_AUTHORIZATIONS");
    }

    @Test
    void businessRejectionDoesNotHaveCause() {
        ConversationAuthorizationService service =
                new ConversationAuthorizationService(FakePolicyRepository.empty(), new FakeAuthorizationStore(false), Duration.ofDays(7));

        Throwable thrown = catchThrowable(() -> service.authorize("agent-a:user-42:conversation-99", "tool-x"));

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException exception = (ApiException) thrown;
        assertThat(exception.code()).isEqualTo(ErrorCode.TOOL_NOT_BOUND);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.context()).isEmpty();
    }

    private void assertPolicyStoreFailure(Throwable thrown, String causeMessage) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException exception = (ApiException) thrown;
        assertThat(exception.code()).isEqualTo(ErrorCode.POLICY_STORE_UNAVAILABLE);
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessage(causeMessage);
        assertThat(exception.context()).containsEntry("operation", "FIND_TOOL_POLICY")
                .containsEntry("agentId", "agent-a")
                .containsEntry("toolId", "tool-x");
    }

    private void assertAuthorizationStoreFailure(Throwable thrown, String causeMessage, String operation) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException exception = (ApiException) thrown;
        assertThat(exception.code()).isEqualTo(ErrorCode.AUTHORIZATION_STORE_UNAVAILABLE);
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessage(causeMessage);
        assertThat(exception.context()).containsEntry("operation", operation)
                .containsEntry("tokenId", "agent-a:user-42:conversation-99")
                .containsEntry("agentId", "agent-a")
                .containsEntry("userId", "user-42")
                .containsEntry("conversationId", "conversation-99");
    }

    private static final class FakePolicyRepository implements ToolPolicyRepository {
        private final Optional<ToolPolicy> policy;
        private final boolean fail;

        private FakePolicyRepository(Optional<ToolPolicy> policy) {
            this(policy, false);
        }

        private FakePolicyRepository(Optional<ToolPolicy> policy, boolean fail) {
            this.policy = policy;
            this.fail = fail;
        }

        static FakePolicyRepository empty() {
            return new FakePolicyRepository(Optional.empty());
        }

        static FakePolicyRepository withPolicy(AuthMode authMode) {
            return new FakePolicyRepository(Optional.of(new ToolPolicy("agent-a", "tool-x", authMode)));
        }

        static FakePolicyRepository failing() {
            return new FakePolicyRepository(Optional.empty(), true);
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            if (fail) {
                throw new IllegalStateException("policy db unavailable");
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
        private final boolean failAuthorize;
        private final boolean failExists;
        private final boolean failCleanup;
        private String authorizedTokenId;
        private String authorizedToolId;
        private Duration authorizedTtl;

        private FakeAuthorizationStore(boolean exists) {
            this(exists, false, false, false);
        }

        private FakeAuthorizationStore(boolean exists, boolean failAuthorize, boolean failExists, boolean failCleanup) {
            this.exists = exists;
            this.failAuthorize = failAuthorize;
            this.failExists = failExists;
            this.failCleanup = failCleanup;
        }

        static FakeAuthorizationStore failingAuthorize() {
            return new FakeAuthorizationStore(false, true, false, false);
        }

        static FakeAuthorizationStore failingExists() {
            return new FakeAuthorizationStore(false, false, true, false);
        }

        static FakeAuthorizationStore failingCleanup() {
            return new FakeAuthorizationStore(false, false, false, true);
        }

        @Override
        public boolean exists(String tokenId, String toolId) {
            if (failExists) {
                throw new IllegalStateException("redis exists unavailable");
            }
            return exists;
        }

        @Override
        public void authorize(String tokenId, String toolId, Duration ttl) {
            if (failAuthorize) {
                throw new IllegalStateException("redis authorize unavailable");
            }
            this.authorizedTokenId = tokenId;
            this.authorizedToolId = toolId;
            this.authorizedTtl = ttl;
        }

        @Override
        public long cleanup(String tokenId) {
            if (failCleanup) {
                throw new IllegalStateException("redis cleanup unavailable");
            }
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
