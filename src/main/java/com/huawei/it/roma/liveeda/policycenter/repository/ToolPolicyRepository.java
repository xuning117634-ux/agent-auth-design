package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;

import java.util.List;
import java.util.Optional;

public interface ToolPolicyRepository {

    Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId);

    List<ToolPolicy> findByAgentId(String agentId);

    void replaceAll(String agentId, List<ToolPolicy> policies);
}
