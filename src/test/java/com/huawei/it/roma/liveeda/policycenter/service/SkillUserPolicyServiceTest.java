package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicySkillCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.SkillUserPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillUserPolicyServiceTest {

    @Test
    void boundSkillsWithoutPolicyDefaultToPublic() {
        FakeCatalog catalog = new FakeCatalog(List.of(
                skill("skill-a", "财经分析"),
                skill("skill-b", "客户洞察")));
        SkillUserPolicyService service = new SkillUserPolicyService(catalog, new FakePolicyRepository());

        SkillUserPolicyListView view = service.listPolicy("agent-a");

        assertThat(view.skills()).extracting(SkillUserPolicyView::skillId)
                .containsExactly("skill-a", "skill-b");
        assertThat(view.skills()).extracting(SkillUserPolicyView::accessScope)
                .containsOnly(AccessScope.PUBLIC);
        assertThat(service.listAccessibleSkills("agent-a", "user-99"))
                .extracting(AccessibleSkillView::skillId)
                .containsExactly("skill-a", "skill-b");
    }

    @Test
    void restrictedSkillsOnlyReturnForWhitelistedUsers() {
        FakePolicyRepository policies = new FakePolicyRepository();
        policies.policies.add(new SkillUserPolicy("agent-a", "skill-a", AccessScope.RESTRICTED));
        policies.policies.add(new SkillUserPolicy("agent-a", "skill-b", AccessScope.RESTRICTED));
        policies.rules.add(new SkillUserAccessRule("agent-a", "skill-a", "user-42"));
        SkillUserPolicyService service = new SkillUserPolicyService(
                new FakeCatalog(List.of(skill("skill-a", "财经分析"), skill("skill-b", "客户洞察"))),
                policies);

        assertThat(service.listAccessibleSkills("agent-a", "user-42"))
                .extracting(AccessibleSkillView::skillId)
                .containsExactly("skill-a");
        assertThat(service.listAccessibleSkills("agent-a", "user-99")).isEmpty();
    }

    @Test
    void replaceRejectsUnboundSkillAndUsesWholeReplacement() {
        FakePolicyRepository policies = new FakePolicyRepository();
        SkillUserPolicyService service = new SkillUserPolicyService(
                new FakeCatalog(List.of(skill("skill-a", "财经分析"), skill("skill-b", "客户洞察"))),
                policies);

        assertThatThrownBy(() -> service.replacePolicy("agent-a", List.of(
                new SkillUserPolicyUpdate("skill-z", AccessScope.RESTRICTED,
                        List.of(new UserAccessUpdate("user-42"))))))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.SKILL_NOT_BOUND));

        SkillUserPolicySaveResult result = service.replacePolicy("agent-a", List.of(
                new SkillUserPolicyUpdate("skill-a", AccessScope.PUBLIC,
                        List.of(new UserAccessUpdate("user-42")))));

        assertThat(result.skillPolicyCount()).isOne();
        assertThat(result.skillUserRuleCount()).isOne();
        assertThat(policies.policies)
                .containsExactly(new SkillUserPolicy("agent-a", "skill-a", AccessScope.PUBLIC));
        assertThat(policies.rules)
                .containsExactly(new SkillUserAccessRule("agent-a", "skill-a", "user-42"));
    }

    @Test
    void duplicateSkillIdsAreRejected() {
        SkillUserPolicyService service = new SkillUserPolicyService(
                new FakeCatalog(List.of(skill("skill-a", "财经分析"))),
                new FakePolicyRepository());

        assertThatThrownBy(() -> service.replacePolicy("agent-a", List.of(
                new SkillUserPolicyUpdate("skill-a", AccessScope.PUBLIC, List.of()),
                new SkillUserPolicyUpdate("skill-a", AccessScope.RESTRICTED, List.of()))))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void policyStoreFailureReturnsServiceUnavailable() {
        SkillUserPolicyRepository unavailableRepository = new SkillUserPolicyRepository() {
            @Override
            public List<SkillUserPolicy> findPolicies(String agentId) {
                throw new IllegalStateException("database unavailable");
            }

            @Override
            public List<SkillUserAccessRule> findUserRules(String agentId) {
                return List.of();
            }

            @Override
            public void replaceAll(
                    String agentId,
                    List<SkillUserPolicy> policies,
                    List<SkillUserAccessRule> rules) {
                throw new IllegalStateException("database unavailable");
            }
        };
        SkillUserPolicyService service = new SkillUserPolicyService(
                new FakeCatalog(List.of(skill("skill-a", "Finance Analysis"))),
                unavailableRepository);

        assertThatThrownBy(() -> service.listPolicy("agent-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.POLICY_STORE_UNAVAILABLE));
        assertThatThrownBy(() -> service.replacePolicy("agent-a", List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.POLICY_STORE_UNAVAILABLE));
    }

    private static AgentPolicySkill skill(String skillId, String skillName) {
        return new AgentPolicySkill("agent-a", skillId, skillName, "finance", skillName + "描述");
    }

    private static final class FakeCatalog implements AgentPolicySkillCatalogRepository {
        private final List<AgentPolicySkill> skills;

        private FakeCatalog(List<AgentPolicySkill> skills) {
            this.skills = skills;
        }

        @Override
        public List<AgentPolicySkill> findBoundSkills(String agentId) {
            return skills.stream().filter(skill -> skill.agentId().equals(agentId)).toList();
        }
    }

    private static final class FakePolicyRepository implements SkillUserPolicyRepository {
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
