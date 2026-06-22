package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;

import java.util.List;

public interface SkillUserPolicyRepository {

    List<SkillUserPolicy> findPolicies(String agentId);

    List<SkillUserAccessRule> findUserRules(String agentId);

    void replaceAll(
            String agentId,
            List<SkillUserPolicy> policies,
            List<SkillUserAccessRule> rules);
}
