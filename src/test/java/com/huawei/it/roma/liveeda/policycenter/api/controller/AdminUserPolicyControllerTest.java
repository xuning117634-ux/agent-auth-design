package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.UserPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminUserPolicyControllerTest {

    private final CapturingUserPolicyRepository userPolicies = new CapturingUserPolicyRepository();
    private final FixedToolPolicyRepository toolPolicies = new FixedToolPolicyRepository();
    private final UserPolicyService service = new UserPolicyService(userPolicies, toolPolicies);
    private final MockMvc mockMvc = standaloneSetup(new AdminUserPolicyController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void returnsPublicAgentAndAllBoundToolsByDefault() throws Exception {
        mockMvc.perform(get("/admin/agents/agent-a/user-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.accessScope").value("PUBLIC"))
                .andExpect(jsonPath("$.agentUsers").isEmpty())
                .andExpect(jsonPath("$.tools.length()").value(2))
                .andExpect(jsonPath("$.tools[0].toolId").value("tool-a"))
                .andExpect(jsonPath("$.tools[0].accessScope").value("PUBLIC"))
                .andExpect(jsonPath("$.tools[0].users").isEmpty())
                .andExpect(jsonPath("$.tools[1].toolId").value("tool-b"))
                .andExpect(jsonPath("$.tools[1].accessScope").value("PUBLIC"));
    }

    @Test
    void replacesUserPolicyWithAccessScopesAndWhitelists() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessScope": "RESTRICTED",
                                  "agentUsers": [
                                    {"userId": "user-42"}
                                  ],
                                  "tools": [
                                    {
                                      "toolId": "tool-a",
                                      "accessScope": "PUBLIC",
                                      "users": [
                                        {"userId": "user-42"}
                                      ]
                                    },
                                    {
                                      "toolId": "tool-b",
                                      "accessScope": "RESTRICTED",
                                      "users": [
                                        {"userId": "user-99"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.agentUserRuleCount").value(1))
                .andExpect(jsonPath("$.toolUserRuleCount").value(2))
                .andExpect(jsonPath("$.updatedAt").isString());

        assertThat(userPolicies.agentPolicy)
                .contains(new AgentUserPolicy("agent-a", AccessScope.RESTRICTED));
        assertThat(userPolicies.agentRules)
                .containsExactly(new UserAccessRule("agent-a", "user-42"));
        assertThat(userPolicies.toolPolicies).containsExactly(
                new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC),
                new ToolUserPolicy("agent-a", "tool-b", AccessScope.RESTRICTED));
        assertThat(userPolicies.toolRules).containsExactly(
                new ToolUserAccessRule("agent-a", "tool-a", "user-42"),
                new ToolUserAccessRule("agent-a", "tool-b", "user-99"));
    }

    @Test
    void expandsBatchUserIdsFromCommonSeparatorsAndRemovesDuplicates() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessScope": "RESTRICTED",
                                  "agentUsers": [
                                    {"userId": "z123,c456; z123\\nd789"}
                                  ],
                                  "tools": [
                                    {
                                      "toolId": "tool-a",
                                      "accessScope": "RESTRICTED",
                                      "users": [
                                        {"userId": "z123，c456；z123"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentUserRuleCount").value(3))
                .andExpect(jsonPath("$.toolUserRuleCount").value(2));

        assertThat(userPolicies.agentRules).containsExactly(
                new UserAccessRule("agent-a", "z123"),
                new UserAccessRule("agent-a", "c456"),
                new UserAccessRule("agent-a", "d789"));
        assertThat(userPolicies.toolRules).containsExactly(
                new ToolUserAccessRule("agent-a", "tool-a", "z123"),
                new ToolUserAccessRule("agent-a", "tool-a", "c456"));
    }

    @Test
    void rejectsBatchUserIdWithoutAnyValidValue() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessScope": "RESTRICTED",
                                  "agentUsers": [
                                    {"userId": ",;，；\\n"}
                                  ],
                                  "tools": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsUnboundToolUserPolicy() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessScope": "PUBLIC",
                                  "agentUsers": [],
                                  "tools": [
                                    {
                                      "toolId": "tool-z",
                                      "accessScope": "RESTRICTED",
                                      "users": [
                                        {"userId": "user-42"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_BOUND"));
    }

    private static final class CapturingUserPolicyRepository implements UserPolicyRepository {
        private Optional<AgentUserPolicy> agentPolicy = Optional.empty();
        private final List<ToolUserPolicy> toolPolicies = new ArrayList<>();
        private final List<UserAccessRule> agentRules = new ArrayList<>();
        private final List<ToolUserAccessRule> toolRules = new ArrayList<>();

        @Override
        public Optional<AgentUserPolicy> findAgentPolicy(String agentId) {
            return agentPolicy;
        }

        @Override
        public List<ToolUserPolicy> findToolPolicies(String agentId) {
            return toolPolicies;
        }

        @Override
        public Optional<ToolUserPolicy> findToolPolicy(String agentId, String toolId) {
            return toolPolicies.stream().filter(policy -> policy.toolId().equals(toolId)).findFirst();
        }

        @Override
        public List<UserAccessRule> findAgentUserRules(String agentId) {
            return agentRules;
        }

        @Override
        public List<ToolUserAccessRule> findToolUserRules(String agentId) {
            return toolRules;
        }

        @Override
        public boolean existsAgentUser(String agentId, String userId) {
            return agentRules.stream().anyMatch(rule -> rule.userId().equals(userId));
        }

        @Override
        public boolean existsToolUser(String agentId, String toolId, String userId) {
            return toolRules.stream()
                    .anyMatch(rule -> rule.toolId().equals(toolId) && rule.userId().equals(userId));
        }

        @Override
        public List<AgentAccessDecision> findAccessibleAgents(String userId) {
            return List.of(AgentAccessDecision.allow(
                    "agent-a",
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
            this.agentPolicy = Optional.of(agentPolicy);
            this.toolPolicies.clear();
            this.toolPolicies.addAll(toolPolicies);
            this.agentRules.clear();
            this.agentRules.addAll(agentRules);
            this.toolRules.clear();
            this.toolRules.addAll(toolRules);
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
            return List.of(
                    new ToolPolicy(agentId, "tool-a", AuthMode.NO_AUTH_REQUIRED),
                    new ToolPolicy(agentId, "tool-b", AuthMode.USER_AUTH_REQUIRED));
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            throw new UnsupportedOperationException();
        }
    }
}
