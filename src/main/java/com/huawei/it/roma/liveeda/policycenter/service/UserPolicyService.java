package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicyToolCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.UserPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserPolicyService implements ToolUserPolicyEvaluator {

    private final UserPolicyRepository repository;
    private final ToolPolicyRepository toolPolicyRepository;
    private final AgentPolicyToolCatalogRepository catalogRepository;
    private final Clock clock;
    private final AuditLogger auditLogger;

    public UserPolicyService(UserPolicyRepository repository, ToolPolicyRepository toolPolicyRepository) {
        this(repository, toolPolicyRepository, AgentPolicyToolCatalogRepository.empty());
    }

    public UserPolicyService(
            UserPolicyRepository repository,
            ToolPolicyRepository toolPolicyRepository,
            AgentPolicyToolCatalogRepository catalogRepository) {
        this(repository, toolPolicyRepository, catalogRepository, Clock.systemUTC(), NoopAuditLogger.INSTANCE);
    }

    @Autowired
    public UserPolicyService(
            UserPolicyRepository repository,
            ToolPolicyRepository toolPolicyRepository,
            AgentPolicyToolCatalogRepository catalogRepository,
            AuditLogger auditLogger) {
        this(repository, toolPolicyRepository, catalogRepository, Clock.systemUTC(), auditLogger);
    }

    UserPolicyService(
            UserPolicyRepository repository,
            ToolPolicyRepository toolPolicyRepository,
            AgentPolicyToolCatalogRepository catalogRepository,
            Clock clock,
            AuditLogger auditLogger) {
        this.repository = repository;
        this.toolPolicyRepository = toolPolicyRepository;
        this.catalogRepository = catalogRepository;
        this.clock = clock;
        this.auditLogger = auditLogger;
    }

    public UserPolicyView listPolicy(String agentId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        try {
            AgentUserPolicy agentPolicy = repository.findAgentPolicy(agentId)
                    .orElse(defaultAgentPolicy(agentId));
            Map<String, ToolUserPolicy> configuredTools = repository.findToolPolicies(agentId).stream()
                    .collect(Collectors.toMap(
                            ToolUserPolicy::toolId,
                            Function.identity(),
                            (left, right) -> right));
            Map<String, List<UserAccessRule>> toolUsers = repository.findToolUserRules(agentId).stream()
                    .collect(Collectors.groupingBy(
                            ToolUserAccessRule::toolId,
                            LinkedHashMap::new,
                            Collectors.mapping(
                                    rule -> new UserAccessRule(rule.agentId(), rule.userId(), rule.updatedAt()),
                                    Collectors.toList())));

            List<UserAccessRule> agentUsers = repository.findAgentUserRules(agentId).stream()
                    .sorted(Comparator.comparing(UserAccessRule::userId))
                    .toList();
            List<ToolUserPolicyView> tools = toolPolicyRepository.findByAgentId(agentId).stream()
                    .sorted(Comparator.comparing(ToolPolicy::toolId))
                    .map(tool -> {
                        ToolUserPolicy policy = configuredTools.getOrDefault(
                                tool.toolId(),
                                defaultToolPolicy(agentId, tool.toolId()));
                        List<UserAccessRule> users = toolUsers.getOrDefault(tool.toolId(), List.of()).stream()
                                .sorted(Comparator.comparing(UserAccessRule::userId))
                                .toList();
                        return new ToolUserPolicyView(
                                tool.toolId(),
                                policy.effectiveAccessScope(),
                                users);
                    })
                    .toList();

            return new UserPolicyView(
                    agentId,
                    agentPolicy.effectiveAccessScope(),
                    agentUsers,
                    tools,
                    agentPolicy.updatedAt());
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    public UserPolicySaveResult replacePolicy(String agentId, UserPolicyUpdate update) {
        ensureNotBlank(agentId, "agentId must not be blank");
        if (update == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "policy must not be null");
        }
        if (update.agentUsers() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "agentUsers must not be null");
        }
        if (update.tools() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "tools must not be null");
        }

        AgentUserPolicy agentPolicy = new AgentUserPolicy(
                agentId,
                effectiveScope(update.accessScope()));
        List<UserAccessRule> agentRules = toAgentRules(agentId, update.agentUsers());
        ToolPolicyReplacement toolReplacement = toToolReplacement(
                agentId,
                update.tools(),
                boundToolIds(agentId));

        try {
            repository.replaceAll(
                    agentId,
                    agentPolicy,
                    toolReplacement.policies(),
                    agentRules,
                    toolReplacement.rules());
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
        auditReplacement(
                agentId,
                agentPolicy.effectiveAccessScope(),
                agentRules.size(),
                toolReplacement.policies().size(),
                toolReplacement.rules().size());
        return new UserPolicySaveResult(
                agentId,
                agentRules.size(),
                toolReplacement.rules().size(),
                Instant.now(clock));
    }

    public AgentAccessDecision decideAgentAccess(String agentId, String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        try {
            AgentUserPolicy policy = repository.findAgentPolicy(agentId)
                    .orElse(defaultAgentPolicy(agentId));
            if (policy.effectiveAccessScope() == AccessScope.PUBLIC) {
                return AgentAccessDecision.allow(
                        agentId,
                        userId,
                        AgentAccessReason.AGENT_PUBLIC_ACCESS);
            }
            if (repository.existsAgentUser(agentId, userId)) {
                return AgentAccessDecision.allow(
                        agentId,
                        userId,
                        AgentAccessReason.AGENT_USER_WHITELISTED);
            }
            return AgentAccessDecision.deny(
                    agentId,
                    userId,
                    AgentAccessReason.AGENT_USER_NOT_WHITELISTED);
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    public List<AgentAccessDecision> listAccessibleAgents(String userId) {
        ensureNotBlank(userId, "userId must not be blank");
        try {
            return repository.findAccessibleAgents(userId);
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    public List<AccessibleToolView> listAccessibleTools(String agentId, String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        try {
            List<ToolPolicy> accessiblePolicies = toolPolicyRepository.findByAgentId(agentId).stream()
                    .filter(policy -> canAccessTool(agentId, policy.toolId(), userId))
                    .map(policy -> new ToolPolicy(
                            policy.agentId(),
                            policy.toolId(),
                            policy.effectiveAuthMode(),
                            policy.updatedAt()))
                    .toList();
            Map<String, AgentPolicyTool> catalogByToolId = catalogByToolId(agentId, accessiblePolicies);
            return accessiblePolicies.stream()
                    .map(policy -> toAccessibleToolView(policy, catalogByToolId.get(policy.toolId())))
                    .toList();
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    @Override
    public boolean canAccessTool(String agentId, String toolId, String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(toolId, "toolId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        try {
            ToolUserPolicy policy = repository.findToolPolicy(agentId, toolId)
                    .orElse(defaultToolPolicy(agentId, toolId));
            return policy.effectiveAccessScope() == AccessScope.PUBLIC
                    || repository.existsToolUser(agentId, toolId, userId);
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    private List<UserAccessRule> toAgentRules(String agentId, List<UserAccessUpdate> updates) {
        List<UserAccessRule> rules = new ArrayList<>();
        Set<String> seenUserIds = new HashSet<>();
        for (UserAccessUpdate update : updates) {
            validateUserUpdate(update, seenUserIds);
            rules.add(new UserAccessRule(agentId, update.userId()));
        }
        return rules;
    }

    private ToolPolicyReplacement toToolReplacement(
            String agentId,
            List<ToolUserPolicyUpdate> updates,
            Set<String> boundToolIds) {
        List<ToolUserPolicy> policies = new ArrayList<>();
        List<ToolUserAccessRule> rules = new ArrayList<>();
        Set<String> seenToolIds = new HashSet<>();
        for (ToolUserPolicyUpdate update : updates) {
            if (update == null || update.toolId() == null || update.toolId().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "toolId must not be blank");
            }
            if (!boundToolIds.contains(update.toolId())) {
                throw new ApiException(ErrorCode.TOOL_NOT_BOUND, "tool is not bound");
            }
            if (!seenToolIds.add(update.toolId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "duplicate toolId: " + update.toolId());
            }
            if (update.users() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "users must not be null");
            }

            policies.add(new ToolUserPolicy(
                    agentId,
                    update.toolId(),
                    effectiveScope(update.accessScope())));
            Set<String> seenUserIds = new HashSet<>();
            for (UserAccessUpdate user : update.users()) {
                validateUserUpdate(user, seenUserIds);
                rules.add(new ToolUserAccessRule(agentId, update.toolId(), user.userId()));
            }
        }
        return new ToolPolicyReplacement(policies, rules);
    }

    private void validateUserUpdate(UserAccessUpdate update, Set<String> seenUserIds) {
        if (update == null || update.userId() == null || update.userId().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "userId must not be blank");
        }
        if (!seenUserIds.add(update.userId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "duplicate userId: " + update.userId());
        }
    }

    private Set<String> boundToolIds(String agentId) {
        try {
            return toolPolicyRepository.findByAgentId(agentId).stream()
                    .map(ToolPolicy::toolId)
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    private AccessScope effectiveScope(AccessScope accessScope) {
        return accessScope == null ? AccessScope.PUBLIC : accessScope;
    }

    private AgentUserPolicy defaultAgentPolicy(String agentId) {
        return new AgentUserPolicy(agentId, AccessScope.PUBLIC);
    }

    private ToolUserPolicy defaultToolPolicy(String agentId, String toolId) {
        return new ToolUserPolicy(agentId, toolId, AccessScope.PUBLIC);
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private ApiException policyStoreUnavailable(RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(ErrorCode.POLICY_STORE_UNAVAILABLE, "policy store is unavailable", exception);
    }

    private Map<String, AgentPolicyTool> catalogByToolId(String agentId, List<ToolPolicy> policies) {
        List<String> toolIds = policies.stream()
                .map(ToolPolicy::toolId)
                .toList();
        return catalogRepository.findBoundTools(agentId, toolIds).stream()
                .collect(Collectors.toMap(
                        AgentPolicyTool::toolId,
                        Function.identity(),
                        (left, right) -> left));
    }

    private AccessibleToolView toAccessibleToolView(ToolPolicy policy, AgentPolicyTool catalog) {
        return new AccessibleToolView(
                catalog == null ? null : catalog.effectiveServerName(),
                catalog == null ? null : catalog.toolName(),
                policy.toolId(),
                policy.effectiveAuthMode());
    }

    private void auditReplacement(
            String agentId,
            AccessScope accessScope,
            int agentUserRuleCount,
            int toolPolicyCount,
            int toolUserRuleCount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventType", "USER_POLICY_REPLACED");
        fields.put("agentId", agentId);
        fields.put("accessScope", accessScope.name());
        fields.put("agentUserRuleCount", Integer.toString(agentUserRuleCount));
        fields.put("toolPolicyCount", Integer.toString(toolPolicyCount));
        fields.put("toolUserRuleCount", Integer.toString(toolUserRuleCount));
        auditLogger.record("USER_POLICY_REPLACED", fields);
    }

    private record ToolPolicyReplacement(
            List<ToolUserPolicy> policies,
            List<ToolUserAccessRule> rules) {
    }
}
