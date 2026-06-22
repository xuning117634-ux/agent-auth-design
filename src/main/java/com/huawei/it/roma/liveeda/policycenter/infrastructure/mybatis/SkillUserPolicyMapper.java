package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SkillUserPolicyMapper {
    List<SkillUserPolicyRecord> selectPolicies(@Param("agentId") String agentId);

    List<SkillUserAccessPolicyRecord> selectUserRules(@Param("agentId") String agentId);

    void deleteUserRules(@Param("agentId") String agentId);

    void deletePolicies(@Param("agentId") String agentId);

    void insertPolicy(SkillUserPolicyRecord row);

    void insertUserRule(SkillUserAccessPolicyRecord row);
}
