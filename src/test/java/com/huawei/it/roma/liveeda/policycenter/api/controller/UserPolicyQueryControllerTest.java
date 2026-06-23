package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicyToolCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.UserPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UserPolicyQueryControllerTest {

    private final UserPolicyService service = new UserPolicyService(
            new FixedUserPolicyRepository(),
            new FixedToolPolicyRepository(),
            new FixedCatalogRepository());
    private final MockMvc mockMvc = standaloneSetup(new UserPolicyQueryController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void returnsWhitelistedAgentAccessDecision() throws Exception {
        mockMvc.perform(post("/internal/agent-access-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "agent-a",
                                  "userId": "user-42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("AGENT_USER_WHITELISTED"));
    }

    @Test
    void returnsPublicAndWhitelistedAgentsForUser() throws Exception {
        mockMvc.perform(get("/internal/users/user-42/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.agents.length()").value(2))
                .andExpect(jsonPath("$.agents[0].agentId").value("agent-a"))
                .andExpect(jsonPath("$.agents[0].reason").value("AGENT_USER_WHITELISTED"))
                .andExpect(jsonPath("$.agents[1].agentId").value("agent-b"))
                .andExpect(jsonPath("$.agents[1].reason").value("AGENT_PUBLIC_ACCESS"));
    }

    @Test
    void returnsPublicAndWhitelistedToolsWithoutCheckingAgentAccess() throws Exception {
        mockMvc.perform(get("/internal/agents/agent-a/users/user-42/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.tools[0].serverId").value("crm-service"))
                .andExpect(jsonPath("$.tools[0].serverName").value("CRM Service"))
                .andExpect(jsonPath("$.tools[0].toolName").value("Customer Query"))
                .andExpect(jsonPath("$.tools[0].toolId").value("tool-a"))
                .andExpect(jsonPath("$.tools[0].authMode").value("NO_AUTH_REQUIRED"))
                .andExpect(jsonPath("$.tools[1].toolId").value("tool-c"))
                .andExpect(jsonPath("$.tools[1].authMode").value("PER_CALL_AUTH_REQUIRED"));
    }

    private static final class FixedUserPolicyRepository implements UserPolicyRepository {

        @Override
        public Optional<AgentUserPolicy> findAgentPolicy(String agentId) {
            if ("agent-a".equals(agentId)) {
                return Optional.of(new AgentUserPolicy(agentId, AccessScope.RESTRICTED));
            }
            return Optional.empty();
        }

        @Override
        public List<ToolUserPolicy> findToolPolicies(String agentId) {
            return List.of(
                    new ToolUserPolicy(agentId, "tool-a", AccessScope.RESTRICTED),
                    new ToolUserPolicy(agentId, "tool-b", AccessScope.RESTRICTED));
        }

        @Override
        public Optional<ToolUserPolicy> findToolPolicy(String agentId, String toolId) {
            return findToolPolicies(agentId).stream()
                    .filter(policy -> policy.toolId().equals(toolId))
                    .findFirst();
        }

        @Override
        public List<UserAccessRule> findAgentUserRules(String agentId) {
            return "agent-a".equals(agentId)
                    ? List.of(new UserAccessRule(agentId, "user-42"))
                    : List.of();
        }

        @Override
        public List<ToolUserAccessRule> findToolUserRules(String agentId) {
            return List.of(
                    new ToolUserAccessRule(agentId, "tool-a", "user-42"),
                    new ToolUserAccessRule(agentId, "tool-b", "user-99"));
        }

        @Override
        public boolean existsAgentUser(String agentId, String userId) {
            return findAgentUserRules(agentId).stream().anyMatch(rule -> rule.userId().equals(userId));
        }

        @Override
        public boolean existsToolUser(String agentId, String toolId, String userId) {
            return findToolUserRules(agentId).stream()
                    .anyMatch(rule -> rule.toolId().equals(toolId) && rule.userId().equals(userId));
        }

        @Override
        public List<AgentAccessDecision> findAccessibleAgents(String userId) {
            return List.of(
                    AgentAccessDecision.allow(
                            "agent-a",
                            userId,
                            AgentAccessReason.AGENT_USER_WHITELISTED),
                    AgentAccessDecision.allow(
                            "agent-b",
                            userId,
                            AgentAccessReason.AGENT_PUBLIC_ACCESS));
        }

        @Override
        public void replaceAll(
                String agentId,
                AgentUserPolicy agentPolicy,
                List<ToolUserPolicy> toolPolicies,
                List<UserAccessRule> agentRules,
                List<ToolUserAccessRule> toolRules) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FixedToolPolicyRepository implements ToolPolicyRepository {

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return findByAgentId(agentId).stream()
                    .filter(policy -> policy.toolId().equals(toolId))
                    .findFirst();
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            if ("agent-a".equals(agentId)) {
                return List.of(
                        new ToolPolicy(agentId, "tool-a", AuthMode.NO_AUTH_REQUIRED),
                        new ToolPolicy(agentId, "tool-b", AuthMode.NO_AUTH_REQUIRED),
                        new ToolPolicy(agentId, "tool-c", AuthMode.PER_CALL_AUTH_REQUIRED));
            }
            return List.of();
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FixedCatalogRepository implements AgentPolicyToolCatalogRepository {

        @Override
        public Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AgentPolicyTool> findBoundTools(String agentId, List<String> toolIds) {
            return toolIds.stream()
                    .filter("tool-a"::equals)
                    .map(toolId -> new AgentPolicyTool(
                            agentId,
                            "crm-service",
                            "CRM Service",
                            "Customer Query",
                            toolId))
                    .toList();
        }
    }
}
