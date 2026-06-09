package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.ConversationAuthorizationService;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ConversationAuthorizationControllerTest {

    private final ConversationAuthorizationService service = new ConversationAuthorizationService(
            new FixedPolicyRepository(AuthMode.USER_AUTH_REQUIRED),
            new FixedAuthorizationStore(true, 2),
            Duration.ofDays(7));
    private final MockMvc mockMvc = standaloneSetup(new ConversationAuthorizationController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void confirmsConversationAuthorization() throws Exception {
        mockMvc.perform(post("/internal/conversation-authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokenId": "agent-a:user-42:conversation-99",
                                  "toolId": "tool-x"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.tokenId").value("agent-a:user-42:conversation-99"))
                .andExpect(jsonPath("$.toolId").value("tool-x"));
    }

    @Test
    void returnsCleanupResult() throws Exception {
        mockMvc.perform(post("/internal/conversation-authorizations/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokenId": "agent-a:user-42:conversation-99"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.deletedGrantCount").value(2));
    }

    private static final class FixedPolicyRepository implements ToolPolicyRepository {
        private final AuthMode authMode;

        private FixedPolicyRepository(AuthMode authMode) {
            this.authMode = authMode;
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return Optional.of(new ToolPolicy(agentId, toolId, authMode));
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            return List.of();
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
        }
    }

    private static final class FixedAuthorizationStore implements ConversationAuthorizationStore {
        private final boolean exists;
        private final long deletedCount;

        private FixedAuthorizationStore(boolean exists, long deletedCount) {
            this.exists = exists;
            this.deletedCount = deletedCount;
        }

        @Override
        public boolean exists(String tokenId, String toolId) {
            return exists;
        }

        @Override
        public void authorize(String tokenId, String toolId, Duration ttl) {
        }

        @Override
        public long cleanup(String tokenId) {
            return deletedCount;
        }
    }
}
