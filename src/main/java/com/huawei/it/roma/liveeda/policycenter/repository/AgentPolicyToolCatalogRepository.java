package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;

import java.util.Optional;

public interface AgentPolicyToolCatalogRepository {

    Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName);
}
