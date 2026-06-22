package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentPolicySkillMapper {
    List<AgentPolicySkillRecord> selectBoundSkills(@Param("agentId") String agentId);
}
