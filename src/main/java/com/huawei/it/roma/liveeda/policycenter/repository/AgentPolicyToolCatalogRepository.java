package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;

import java.util.List;
import java.util.Optional;

public interface AgentPolicyToolCatalogRepository {

    Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName);

    List<AgentPolicyTool> findBoundTools(String agentId, List<String> toolIds);

    static AgentPolicyToolCatalogRepository empty() {
        return new AgentPolicyToolCatalogRepository() {
            @Override
            public Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName) {
                return Optional.empty();
            }

            @Override
            public List<AgentPolicyTool> findBoundTools(String agentId, List<String> toolIds) {
                return List.of();
            }
        };
    }
}
