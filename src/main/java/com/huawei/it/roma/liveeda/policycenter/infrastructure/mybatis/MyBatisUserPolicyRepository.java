package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.repository.UserPolicyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisUserPolicyRepository implements UserPolicyRepository {

    private final UserPolicyMapper mapper;

    public MyBatisUserPolicyRepository(UserPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentUserPolicy> findAgentPolicy(String agentId) {
        return Optional.ofNullable(mapper.selectAgentPolicy(agentId))
                .map(this::toDomain);
    }

    @Override
    public List<ToolUserPolicy> findToolPolicies(String agentId) {
        return mapper.selectToolPolicies(agentId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ToolUserPolicy> findToolPolicy(String agentId, String toolId) {
        return Optional.ofNullable(mapper.selectToolPolicy(agentId, toolId))
                .map(this::toDomain);
    }

    @Override
    public List<UserAccessRule> findAgentUserRules(String agentId) {
        return mapper.selectAgentUserRules(agentId).stream()
                .map(this::toAgentRuleDomain)
                .toList();
    }

    @Override
    public List<ToolUserAccessRule> findToolUserRules(String agentId) {
        return mapper.selectToolUserRules(agentId).stream()
                .map(this::toToolRuleDomain)
                .toList();
    }

    @Override
    public boolean existsAgentUser(String agentId, String userId) {
        return mapper.countAgentUser(agentId, userId) > 0;
    }

    @Override
    public boolean existsToolUser(String agentId, String toolId, String userId) {
        return mapper.countToolUser(agentId, toolId, userId) > 0;
    }

    @Override
    public List<AgentAccessDecision> findAccessibleAgents(String userId) {
        return mapper.selectAccessibleAgents(userId).stream()
                .map(record -> toAgentAccessDecision(record, userId))
                .toList();
    }

    @Transactional
    @Override
    public void replaceAll(
            String agentId,
            AgentUserPolicy agentPolicy,
            List<ToolUserPolicy> toolPolicies,
            List<UserAccessRule> agentRules,
            List<ToolUserAccessRule> toolRules) {
        mapper.upsertAgentPolicy(toRecord(agentPolicy));
        mapper.deleteAgentUserRules(agentId);
        mapper.deleteToolUserRules(agentId);
        mapper.deleteToolPolicies(agentId);
        for (ToolUserPolicy policy : toolPolicies) {
            mapper.insertToolPolicy(toRecord(policy));
        }
        for (UserAccessRule rule : agentRules) {
            mapper.insertAgentUserRule(toRecord(rule));
        }
        for (ToolUserAccessRule rule : toolRules) {
            mapper.insertToolUserRule(toRecord(rule));
        }
    }

    private AgentUserPolicy toDomain(AgentUserPolicyRecord record) {
        AccessScope accessScope = record.getAccessScope() == null
                ? AccessScope.PUBLIC
                : AccessScope.valueOf(record.getAccessScope());
        return new AgentUserPolicy(record.getAgentId(), accessScope, record.getUpdatedAt());
    }

    private ToolUserPolicy toDomain(ToolUserPolicyRecord record) {
        AccessScope accessScope = record.getAccessScope() == null
                ? AccessScope.PUBLIC
                : AccessScope.valueOf(record.getAccessScope());
        return new ToolUserPolicy(
                record.getAgentId(),
                record.getToolId(),
                accessScope,
                record.getUpdatedAt());
    }

    private UserAccessRule toAgentRuleDomain(UserAccessPolicyRecord record) {
        return new UserAccessRule(
                record.getAgentId(),
                record.getUserId(),
                record.getUpdatedAt());
    }

    private ToolUserAccessRule toToolRuleDomain(ToolUserAccessPolicyRecord record) {
        return new ToolUserAccessRule(
                record.getAgentId(),
                record.getToolId(),
                record.getUserId(),
                record.getUpdatedAt());
    }

    private AgentAccessDecision toAgentAccessDecision(AccessibleAgentRecord record, String userId) {
        AccessScope accessScope = record.getAccessScope() == null
                ? AccessScope.PUBLIC
                : AccessScope.valueOf(record.getAccessScope());
        AgentAccessReason reason = accessScope == AccessScope.PUBLIC
                ? AgentAccessReason.AGENT_PUBLIC_ACCESS
                : AgentAccessReason.AGENT_USER_WHITELISTED;
        return AgentAccessDecision.allow(record.getAgentId(), userId, reason);
    }

    private AgentUserPolicyRecord toRecord(AgentUserPolicy policy) {
        AgentUserPolicyRecord record = new AgentUserPolicyRecord();
        record.setAgentId(policy.agentId());
        record.setAccessScope(policy.effectiveAccessScope().name());
        return record;
    }

    private ToolUserPolicyRecord toRecord(ToolUserPolicy policy) {
        ToolUserPolicyRecord record = new ToolUserPolicyRecord();
        record.setAgentId(policy.agentId());
        record.setToolId(policy.toolId());
        record.setAccessScope(policy.effectiveAccessScope().name());
        return record;
    }

    private UserAccessPolicyRecord toRecord(UserAccessRule rule) {
        UserAccessPolicyRecord record = new UserAccessPolicyRecord();
        record.setAgentId(rule.agentId());
        record.setUserId(rule.userId());
        return record;
    }

    private ToolUserAccessPolicyRecord toRecord(ToolUserAccessRule rule) {
        ToolUserAccessPolicyRecord record = new ToolUserAccessPolicyRecord();
        record.setAgentId(rule.agentId());
        record.setToolId(rule.toolId());
        record.setUserId(rule.userId());
        return record;
    }
}
