package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.DecisionReason;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.AuthorizationDecisionService;
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

class AuthorizationDecisionControllerTest {

    private final AuthorizationDecisionService service = new AuthorizationDecisionService(
            new FixedPolicyRepository(AuthMode.NO_AUTH_REQUIRED),
            new FixedAuthorizationStore(false));
    private final MockMvc mockMvc = standaloneSetup(new AuthorizationDecisionController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void returnsDecisionResponse() throws Exception {
        mockMvc.perform(post("/internal/authorization-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokenId": "agent-a:user-42:conversation-99",
                                  "toolId": "tool-x"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.reason").value("NO_AUTH_REQUIRED"));
    }

    @Test
    void invalidRequestReturnsErrorResponseWithTraceId() throws Exception {
        mockMvc.perform(post("/internal/authorization-decisions")
                        .header("X-Trace-Id", "trace-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokenId": "agent-a:user-42:conversation-99",
                                  "toolId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
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

        private FixedAuthorizationStore(boolean exists) {
            this.exists = exists;
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
            return 0;
        }
    }
}
