package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserPolicyMapper {

    AgentUserPolicyRecord selectAgentPolicy(@Param("agentId") String agentId);

    List<ToolUserPolicyRecord> selectToolPolicies(@Param("agentId") String agentId);

    ToolUserPolicyRecord selectToolPolicy(
            @Param("agentId") String agentId,
            @Param("toolId") String toolId);

    List<UserAccessPolicyRecord> selectAgentUserRules(@Param("agentId") String agentId);

    List<ToolUserAccessPolicyRecord> selectToolUserRules(@Param("agentId") String agentId);

    int countAgentUser(
            @Param("agentId") String agentId,
            @Param("userId") String userId);

    int countToolUser(
            @Param("agentId") String agentId,
            @Param("toolId") String toolId,
            @Param("userId") String userId);

    List<AccessibleAgentRecord> selectAccessibleAgents(@Param("userId") String userId);

    void upsertAgentPolicy(AgentUserPolicyRecord record);

    void insertToolPolicy(ToolUserPolicyRecord record);

    void insertAgentUserRule(UserAccessPolicyRecord record);

    void insertToolUserRule(ToolUserAccessPolicyRecord record);

    void deleteToolPolicies(@Param("agentId") String agentId);

    void deleteAgentUserRules(@Param("agentId") String agentId);

    void deleteToolUserRules(@Param("agentId") String agentId);
}
