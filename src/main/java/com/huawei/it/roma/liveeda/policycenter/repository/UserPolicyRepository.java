package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;

import java.util.List;
import java.util.Optional;

public interface UserPolicyRepository {

    Optional<AgentUserPolicy> findAgentPolicy(String agentId);

    List<ToolUserPolicy> findToolPolicies(String agentId);

    Optional<ToolUserPolicy> findToolPolicy(String agentId, String toolId);

    List<UserAccessRule> findAgentUserRules(String agentId);

    List<ToolUserAccessRule> findToolUserRules(String agentId);

    boolean existsAgentUser(String agentId, String userId);

    boolean existsToolUser(String agentId, String toolId, String userId);

    List<String> findKnownAgentIds();

    void replaceAll(
            String agentId,
            AgentUserPolicy agentPolicy,
            List<ToolUserPolicy> toolPolicies,
            List<UserAccessRule> agentRules,
            List<ToolUserAccessRule> toolRules);
}
