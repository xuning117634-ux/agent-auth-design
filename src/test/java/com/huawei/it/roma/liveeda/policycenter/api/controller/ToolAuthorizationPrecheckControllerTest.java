package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicyToolCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.AuthorizationDecisionService;
import com.huawei.it.roma.liveeda.policycenter.service.ToolAuthorizationPrecheckService;
import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ToolAuthorizationPrecheckControllerTest {

    @Test
    void returnsOkWithTokenIdAndAuthorizationRequiredTools() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.USER_AUTH_REQUIRED, false);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['X-AGW-ACCESS-TOKEN']").doesNotExist())
                .andExpect(jsonPath("$.tokenid").doesNotExist())
                .andExpect(jsonPath("$.tokenId").doesNotExist())
                .andExpect(jsonPath("$.tools[0].serverId").value("finance-server"))
                .andExpect(jsonPath("$.tools[0].serverName").value("财经服务"))
                .andExpect(jsonPath("$.tools[0].toolName").value("quoteQuery"))
                .andExpect(jsonPath("$.tools[0].toolId").value("finance.quote.query"))
                .andExpect(jsonPath("$.tools[0].decision").value("AUTHORIZATION_REQUIRED"));
    }

    @Test
    void returnsOkWithAgwAccessTokenHeader() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.USER_AUTH_REQUIRED, false);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("X-AGW-ACCESS-TOKEN", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['X-AGW-ACCESS-TOKEN']").doesNotExist())
                .andExpect(jsonPath("$.tokenid").doesNotExist())
                .andExpect(jsonPath("$.tokenId").doesNotExist())
                .andExpect(jsonPath("$.tools[0].decision").value("AUTHORIZATION_REQUIRED"));
    }

    @Test
    void prefersAgwAccessTokenOverLegacyTokenIdHeaderForPrecheck() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.USER_AUTH_REQUIRED, false);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("X-AGW-ACCESS-TOKEN", "agent-new:user-42:conversation-99")
                        .header("tokenid", "agent-old:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['X-AGW-ACCESS-TOKEN']").doesNotExist())
                .andExpect(jsonPath("$.tokenid").doesNotExist())
                .andExpect(jsonPath("$.tokenId").doesNotExist());
    }

    @Test
    void returnsOkWithTokenIdAndEmptyToolsWhenNoAuthorizationRequired() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.NO_AUTH_REQUIRED, false);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverid": "finance-server",
                                      "toolname": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['X-AGW-ACCESS-TOKEN']").doesNotExist())
                .andExpect(jsonPath("$.tokenid").doesNotExist())
                .andExpect(jsonPath("$.tokenId").doesNotExist())
                .andExpect(jsonPath("$.tools").isEmpty());
    }

    @Test
    void perCallPrecheckDoesNotConsumeExistingOneTimeAuthorization() throws Exception {
        FixedAuthorizationStore authorizationStore = new FixedAuthorizationStore(true);
        MockMvc mockMvc = mockMvc(
                new FixedCatalogRepository(false),
                new FixedPolicyRepository(AuthMode.PER_CALL_AUTH_REQUIRED),
                authorizationStore);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isEmpty());

        assertThat(authorizationStore.consumeCallCount).isZero();
    }

    @Test
    void perCallPrecheckReturnsRequiredToolWhenOneTimeAuthorizationIsMissing() throws Exception {
        FixedAuthorizationStore authorizationStore = new FixedAuthorizationStore(false);
        MockMvc mockMvc = mockMvc(
                new FixedCatalogRepository(false),
                new FixedPolicyRepository(AuthMode.PER_CALL_AUTH_REQUIRED),
                authorizationStore);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[0].toolId").value("finance.quote.query"))
                .andExpect(jsonPath("$.tools[0].decision").value("AUTHORIZATION_REQUIRED"));

        assertThat(authorizationStore.consumeCallCount).isZero();
    }

    @Test
    void rejectsUnknownCatalogTool() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.USER_AUTH_REQUIRED, true);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "missing-server",
                                      "toolName": "missingTool"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingTokenIdHeader() throws Exception {
        MockMvc mockMvc = mockMvc(AuthMode.USER_AUTH_REQUIRED, false);

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsServiceUnavailableWhenPolicyStoreFails() throws Exception {
        MockMvc mockMvc = mockMvc(
                new FixedCatalogRepository(false),
                new FailingPolicyRepository(),
                new FixedAuthorizationStore(false));

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POLICY_STORE_UNAVAILABLE"));
    }

    @Test
    void returnsServiceUnavailableWhenAuthorizationStoreFails() throws Exception {
        MockMvc mockMvc = mockMvc(
                new FixedCatalogRepository(false),
                new FixedPolicyRepository(AuthMode.USER_AUTH_REQUIRED),
                new FailingAuthorizationStore());

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("tokenid", "agent-a:user-42:conversation-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {
                                      "serverId": "finance-server",
                                      "toolName": "quoteQuery"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_STORE_UNAVAILABLE"));
    }

    private MockMvc mockMvc(AuthMode authMode, boolean emptyCatalog) {
        return mockMvc(
                new FixedCatalogRepository(emptyCatalog),
                new FixedPolicyRepository(authMode),
                new FixedAuthorizationStore(false));
    }

    private MockMvc mockMvc(
            AgentPolicyToolCatalogRepository catalogRepository,
            ToolPolicyRepository policyRepository,
            ConversationAuthorizationStore authorizationStore) {
        AuthorizationDecisionService decisionService = new AuthorizationDecisionService(policyRepository, authorizationStore);
        ToolAuthorizationPrecheckService service = new ToolAuthorizationPrecheckService(
                catalogRepository,
                decisionService);
        return standaloneSetup(new ToolAuthorizationPrecheckController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(MockMvcSupport.jsonConverter())
                .build();
    }

    private static final class FixedCatalogRepository implements AgentPolicyToolCatalogRepository {
        private final boolean empty;

        private FixedCatalogRepository(boolean empty) {
            this.empty = empty;
        }

        @Override
        public Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName) {
            if (empty) {
                return Optional.empty();
            }
            return Optional.of(new AgentPolicyTool(
                    agentId,
                    serverId,
                    "财经服务",
                    toolName,
                    "finance.quote.query"));
        }
        @Override
        public List<AgentPolicyTool> findBoundTools(String agentId, List<String> toolIds) {
            return List.of();
        }
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

    private static final class FailingPolicyRepository implements ToolPolicyRepository {

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            throw new IllegalStateException("policy store down");
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
        private int consumeCallCount;

        private FixedAuthorizationStore(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean exists(String tokenId, String toolId) {
            return exists;
        }

        @Override
        public boolean consume(String tokenId, String toolId) {
            consumeCallCount++;
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

    private static final class FailingAuthorizationStore implements ConversationAuthorizationStore {

        @Override
        public boolean exists(String tokenId, String toolId) {
            throw new IllegalStateException("authorization store down");
        }

        @Override
        public boolean consume(String tokenId, String toolId) {
            throw new IllegalStateException("authorization store down");
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
