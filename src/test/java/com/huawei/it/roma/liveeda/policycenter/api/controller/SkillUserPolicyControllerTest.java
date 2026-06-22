package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicySkillCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.SkillUserPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.SkillUserPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SkillUserPolicyControllerTest {

    private final MutablePolicyRepository policies = new MutablePolicyRepository();
    private final AgentPolicySkillCatalogRepository catalog = agentId -> List.of(
            new AgentPolicySkill(agentId, "skill-a", "财经分析", "finance", "财经数据分析"),
            new AgentPolicySkill(agentId, "skill-b", "客户洞察", "crm", "客户洞察分析"));
    private final SkillUserPolicyService service = new SkillUserPolicyService(catalog, policies);
    private final MockMvc mockMvc = standaloneSetup(
            new AdminSkillUserPolicyController(service),
            new SkillUserPolicyQueryController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void returnsBoundSkillsWithDefaultPublicPolicy() throws Exception {
        mockMvc.perform(get("/admin/agents/agent-a/skill-user-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].skillId").value("skill-a"))
                .andExpect(jsonPath("$.skills[0].skillName").value("财经分析"))
                .andExpect(jsonPath("$.skills[0].accessScope").value("PUBLIC"));
    }

    @Test
    void savesBatchUsersAndReturnsAccessibleSkills() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/skill-user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": [
                                    {
                                      "skillId": "skill-a",
                                      "accessScope": "RESTRICTED",
                                      "users": [{"userId": "z123,c456;z123"}]
                                    },
                                    {
                                      "skillId": "skill-b",
                                      "accessScope": "PUBLIC",
                                      "users": []
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillPolicyCount").value(2))
                .andExpect(jsonPath("$.skillUserRuleCount").value(2));

        mockMvc.perform(get("/internal/agents/agent-a/users/z123/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].skillId").value("skill-a"))
                .andExpect(jsonPath("$.skills[0].label").value("finance"));

        mockMvc.perform(get("/internal/agents/agent-a/users/user-99/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].skillId").value("skill-b"));
    }

    @Test
    void returnsConflictForUnboundSkill() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/skill-user-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": [
                                    {"skillId": "skill-z", "accessScope": "RESTRICTED", "users": []}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKILL_NOT_BOUND"));
    }

    private static final class MutablePolicyRepository implements SkillUserPolicyRepository {
        private final List<SkillUserPolicy> policies = new ArrayList<>();
        private final List<SkillUserAccessRule> rules = new ArrayList<>();

        @Override
        public List<SkillUserPolicy> findPolicies(String agentId) {
            return policies.stream().filter(policy -> policy.agentId().equals(agentId)).toList();
        }

        @Override
        public List<SkillUserAccessRule> findUserRules(String agentId) {
            return rules.stream().filter(rule -> rule.agentId().equals(agentId)).toList();
        }

        @Override
        public void replaceAll(
                String agentId,
                List<SkillUserPolicy> replacements,
                List<SkillUserAccessRule> replacementRules) {
            policies.removeIf(policy -> policy.agentId().equals(agentId));
            policies.addAll(replacements);
            rules.removeIf(rule -> rule.agentId().equals(agentId));
            rules.addAll(replacementRules);
        }
    }
}
