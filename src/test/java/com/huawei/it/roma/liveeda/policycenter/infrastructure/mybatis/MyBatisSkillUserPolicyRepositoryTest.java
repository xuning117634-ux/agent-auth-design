package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisSkillUserPolicyRepositoryTest {

    @Test
    void mapsPoliciesAndRules() {
        FakeMapper mapper = new FakeMapper();
        mapper.policies = List.of(policy("agent-a", "skill-a", AccessScope.RESTRICTED));
        mapper.rules = List.of(rule("agent-a", "skill-a", "z123"));
        var repository = new MyBatisSkillUserPolicyRepository(mapper);

        assertThat(repository.findPolicies("agent-a"))
                .containsExactly(new SkillUserPolicy("agent-a", "skill-a", AccessScope.RESTRICTED, null));
        assertThat(repository.findUserRules("agent-a"))
                .containsExactly(new SkillUserAccessRule("agent-a", "skill-a", "z123", null));
    }

    @Test
    void replacesRowsInForeignKeySafeOrder() {
        FakeMapper mapper = new FakeMapper();
        var repository = new MyBatisSkillUserPolicyRepository(mapper);

        repository.replaceAll(
                "agent-a",
                List.of(new SkillUserPolicy("agent-a", "skill-a", AccessScope.RESTRICTED)),
                List.of(new SkillUserAccessRule("agent-a", "skill-a", "z123")));

        assertThat(mapper.operations).containsExactly(
                "deleteRules:agent-a",
                "deletePolicies:agent-a",
                "insertPolicy:agent-a:skill-a:RESTRICTED",
                "insertRule:agent-a:skill-a:z123");
    }

    private static SkillUserPolicyRecord policy(String agentId, String skillId, AccessScope scope) {
        SkillUserPolicyRecord row = new SkillUserPolicyRecord();
        row.setAgentId(agentId);
        row.setSkillId(skillId);
        row.setAccessScope(scope.name());
        return row;
    }

    private static SkillUserAccessPolicyRecord rule(String agentId, String skillId, String userId) {
        SkillUserAccessPolicyRecord row = new SkillUserAccessPolicyRecord();
        row.setAgentId(agentId);
        row.setSkillId(skillId);
        row.setUserId(userId);
        return row;
    }

    private static final class FakeMapper implements SkillUserPolicyMapper {
        private List<SkillUserPolicyRecord> policies = List.of();
        private List<SkillUserAccessPolicyRecord> rules = List.of();
        private final List<String> operations = new ArrayList<>();

        @Override
        public List<SkillUserPolicyRecord> selectPolicies(String agentId) {
            return policies;
        }

        @Override
        public List<SkillUserAccessPolicyRecord> selectUserRules(String agentId) {
            return rules;
        }

        @Override
        public void deleteUserRules(String agentId) {
            operations.add("deleteRules:" + agentId);
        }

        @Override
        public void deletePolicies(String agentId) {
            operations.add("deletePolicies:" + agentId);
        }

        @Override
        public void insertPolicy(SkillUserPolicyRecord row) {
            operations.add("insertPolicy:%s:%s:%s".formatted(
                    row.getAgentId(), row.getSkillId(), row.getAccessScope()));
        }

        @Override
        public void insertUserRule(SkillUserAccessPolicyRecord row) {
            operations.add("insertRule:%s:%s:%s".formatted(
                    row.getAgentId(), row.getSkillId(), row.getUserId()));
        }
    }
}
