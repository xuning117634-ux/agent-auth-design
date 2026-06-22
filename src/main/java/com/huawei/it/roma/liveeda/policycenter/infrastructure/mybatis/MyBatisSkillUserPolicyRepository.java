package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.SkillUserPolicyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class MyBatisSkillUserPolicyRepository implements SkillUserPolicyRepository {

    private final SkillUserPolicyMapper mapper;

    public MyBatisSkillUserPolicyRepository(SkillUserPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SkillUserPolicy> findPolicies(String agentId) {
        return mapper.selectPolicies(agentId).stream()
                .map(row -> new SkillUserPolicy(
                        row.getAgentId(),
                        row.getSkillId(),
                        row.getAccessScope() == null
                                ? AccessScope.PUBLIC
                                : AccessScope.valueOf(row.getAccessScope()),
                        row.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<SkillUserAccessRule> findUserRules(String agentId) {
        return mapper.selectUserRules(agentId).stream()
                .map(row -> new SkillUserAccessRule(
                        row.getAgentId(),
                        row.getSkillId(),
                        row.getUserId(),
                        row.getUpdatedAt()))
                .toList();
    }

    @Transactional
    @Override
    public void replaceAll(
            String agentId,
            List<SkillUserPolicy> policies,
            List<SkillUserAccessRule> rules) {
        mapper.deleteUserRules(agentId);
        mapper.deletePolicies(agentId);
        policies.forEach(policy -> mapper.insertPolicy(toRecord(policy)));
        rules.forEach(rule -> mapper.insertUserRule(toRecord(rule)));
    }

    private SkillUserPolicyRecord toRecord(SkillUserPolicy policy) {
        SkillUserPolicyRecord row = new SkillUserPolicyRecord();
        row.setAgentId(policy.agentId());
        row.setSkillId(policy.skillId());
        row.setAccessScope(policy.effectiveAccessScope().name());
        return row;
    }

    private SkillUserAccessPolicyRecord toRecord(SkillUserAccessRule rule) {
        SkillUserAccessPolicyRecord row = new SkillUserAccessPolicyRecord();
        row.setAgentId(rule.agentId());
        row.setSkillId(rule.skillId());
        row.setUserId(rule.userId());
        return row;
    }
}
