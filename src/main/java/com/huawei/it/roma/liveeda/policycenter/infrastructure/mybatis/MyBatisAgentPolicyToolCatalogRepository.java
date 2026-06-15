package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicyToolCatalogRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisAgentPolicyToolCatalogRepository implements AgentPolicyToolCatalogRepository {

    private final AgentPolicyToolMapper mapper;

    public MyBatisAgentPolicyToolCatalogRepository(AgentPolicyToolMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentPolicyTool> findBoundTool(String agentId, String serverId, String toolName) {
        return Optional.ofNullable(mapper.selectBoundTool(agentId, serverId, toolName))
                .map(this::toDomain);
    }

    private AgentPolicyTool toDomain(AgentPolicyToolRecord record) {
        return new AgentPolicyTool(
                record.getAgentId(),
                record.getServiceId(),
                record.getServiceName(),
                record.getToolName(),
                record.getToolId());
    }
}
