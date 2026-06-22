package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisAgentPolicySkillCatalogRepositoryTest {

    @Test
    void mapsBoundSkillsFromDirectoryRows() {
        AgentPolicySkillRecord row = new AgentPolicySkillRecord();
        row.setAgentId("agent-a");
        row.setSkillId("skill-a");
        row.setSkillName("Finance Analysis");
        row.setLabel("finance");
        row.setDescription("Analyze finance data");
        AgentPolicySkillMapper mapper = agentId -> List.of(row);

        var repository = new MyBatisAgentPolicySkillCatalogRepository(mapper);

        assertThat(repository.findBoundSkills("agent-a"))
                .containsExactly(new AgentPolicySkill(
                        "agent-a",
                        "skill-a",
                        "Finance Analysis",
                        "finance",
                        "Analyze finance data"));
    }
}
