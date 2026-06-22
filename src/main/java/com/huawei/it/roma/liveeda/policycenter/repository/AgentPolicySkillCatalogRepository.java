package com.huawei.it.roma.liveeda.policycenter.repository;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;

import java.util.List;

public interface AgentPolicySkillCatalogRepository {

    List<AgentPolicySkill> findBoundSkills(String agentId);
}
