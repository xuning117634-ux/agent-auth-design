package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicySkillCatalogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisAgentPolicySkillCatalogRepository implements AgentPolicySkillCatalogRepository {

    private final AgentPolicySkillMapper mapper;

    public MyBatisAgentPolicySkillCatalogRepository(AgentPolicySkillMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AgentPolicySkill> findBoundSkills(String agentId) {
        return mapper.selectBoundSkills(agentId).stream()
                .map(row -> new AgentPolicySkill(
                        row.getAgentId(),
                        row.getSkillId(),
                        row.getSkillName(),
                        row.getLabel(),
                        row.getDescription()))
                .toList();
    }
}
