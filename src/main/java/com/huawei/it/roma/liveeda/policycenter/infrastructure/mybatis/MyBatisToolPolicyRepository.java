package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisToolPolicyRepository implements ToolPolicyRepository {

    private final ToolPolicyMapper mapper;

    public MyBatisToolPolicyRepository(ToolPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
        return Optional.ofNullable(mapper.selectByAgentIdAndToolId(agentId, toolId))
                .map(this::toDomain);
    }

    @Override
    public List<ToolPolicy> findByAgentId(String agentId) {
        return mapper.selectByAgentId(agentId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional
    @Override
    public void replaceAll(String agentId, List<ToolPolicy> policies) {
        if (policies.isEmpty()) {
            mapper.deleteByAgentId(agentId);
            return;
        }

        for (ToolPolicy policy : policies) {
            mapper.upsert(toRecord(policy));
        }
        mapper.deleteMissing(agentId, policies.stream().map(ToolPolicy::toolId).toList());
    }

    private ToolPolicy toDomain(ToolPolicyRecord record) {
        AuthMode authMode = record.getAuthMode() == null ? null : AuthMode.valueOf(record.getAuthMode());
        return new ToolPolicy(record.getAgentId(), record.getToolId(), authMode, record.getUpdatedAt());
    }

    private ToolPolicyRecord toRecord(ToolPolicy policy) {
        ToolPolicyRecord record = new ToolPolicyRecord();
        record.setAgentId(policy.agentId());
        record.setToolId(policy.toolId());
        record.setAuthMode(policy.effectiveAuthMode().name());
        return record;
    }
}
