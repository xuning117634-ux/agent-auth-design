package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.audit.AuditLogger;
import com.huawei.it.roma.liveeda.policycenter.audit.NoopAuditLogger;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicySkill;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.AgentPolicySkillCatalogRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.SkillUserPolicyRepository;
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
public class SkillUserPolicyService {

    private final AgentPolicySkillCatalogRepository catalogRepository;
    private final SkillUserPolicyRepository policyRepository;
    private final Clock clock;
    private final AuditLogger auditLogger;

    public SkillUserPolicyService(
            AgentPolicySkillCatalogRepository catalogRepository,
            SkillUserPolicyRepository policyRepository) {
        this(catalogRepository, policyRepository, Clock.systemUTC(), NoopAuditLogger.INSTANCE);
    }

    @Autowired
    public SkillUserPolicyService(
            AgentPolicySkillCatalogRepository catalogRepository,
            SkillUserPolicyRepository policyRepository,
            AuditLogger auditLogger) {
        this(catalogRepository, policyRepository, Clock.systemUTC(), auditLogger);
    }

    SkillUserPolicyService(
            AgentPolicySkillCatalogRepository catalogRepository,
            SkillUserPolicyRepository policyRepository,
            Clock clock,
            AuditLogger auditLogger) {
        this.catalogRepository = catalogRepository;
        this.policyRepository = policyRepository;
        this.clock = clock;
        this.auditLogger = auditLogger;
    }

    public SkillUserPolicyListView listPolicy(String agentId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        try {
            List<AgentPolicySkill> boundSkills = boundSkills(agentId);
            Map<String, SkillUserPolicy> policies = policyRepository.findPolicies(agentId).stream()
                    .collect(Collectors.toMap(SkillUserPolicy::skillId, Function.identity(), (left, right) -> right));
            Map<String, List<SkillUserAccessRule>> users = policyRepository.findUserRules(agentId).stream()
                    .collect(Collectors.groupingBy(
                            SkillUserAccessRule::skillId,
                            LinkedHashMap::new,
                            Collectors.toList()));
            List<SkillUserPolicyView> views = boundSkills.stream()
                    .map(skill -> toPolicyView(skill, policies.get(skill.skillId()), users.get(skill.skillId())))
                    .toList();
            Instant updatedAt = java.util.stream.Stream.concat(
                            views.stream()
                                    .flatMap(view -> view.users().stream())
                                    .map(SkillUserAccessRule::updatedAt),
                            policies.values().stream().map(SkillUserPolicy::updatedAt))
                    .filter(value -> value != null)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            return new SkillUserPolicyListView(agentId, views, updatedAt);
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
    }

    public SkillUserPolicySaveResult replacePolicy(String agentId, List<SkillUserPolicyUpdate> updates) {
        ensureNotBlank(agentId, "agentId must not be blank");
        if (updates == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "skills must not be null");
        }
        Set<String> boundSkillIds;
        try {
            boundSkillIds = boundSkills(agentId).stream().map(AgentPolicySkill::skillId).collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
        List<SkillUserPolicy> policies = new ArrayList<>();
        List<SkillUserAccessRule> rules = new ArrayList<>();
        Set<String> seenSkillIds = new HashSet<>();
        for (SkillUserPolicyUpdate update : updates) {
            if (update == null || update.skillId() == null || update.skillId().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "skillId must not be blank");
            }
            if (!seenSkillIds.add(update.skillId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "duplicate skillId: " + update.skillId());
            }
            if (!boundSkillIds.contains(update.skillId())) {
                throw new ApiException(ErrorCode.SKILL_NOT_BOUND, "skill is not bound");
            }
            if (update.users() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "users must not be null");
            }
            policies.add(new SkillUserPolicy(agentId, update.skillId(), effectiveScope(update.accessScope())));
            Set<String> seenUsers = new HashSet<>();
            for (UserAccessUpdate user : update.users()) {
                if (user == null || user.userId() == null || user.userId().isBlank()) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "userId must not be blank");
                }
                if (!seenUsers.add(user.userId())) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "duplicate userId: " + user.userId());
                }
                rules.add(new SkillUserAccessRule(agentId, update.skillId(), user.userId()));
            }
        }
        try {
            policyRepository.replaceAll(agentId, policies, rules);
        } catch (RuntimeException exception) {
            throw policyStoreUnavailable(exception);
        }
        auditLogger.record("SKILL_USER_POLICY_REPLACED", Map.of(
                "eventType", "SKILL_USER_POLICY_REPLACED",
                "agentId", agentId,
                "skillPolicyCount", Integer.toString(policies.size()),
                "skillUserRuleCount", Integer.toString(rules.size())));
        return new SkillUserPolicySaveResult(agentId, policies.size(), rules.size(), Instant.now(clock));
    }

    public List<AccessibleSkillView> listAccessibleSkills(String agentId, String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        SkillUserPolicyListView view = listPolicy(agentId);
        return view.skills().stream()
                .filter(skill -> skill.accessScope() == AccessScope.PUBLIC
                        || skill.users().stream().anyMatch(rule -> rule.userId().equals(userId)))
                .map(skill -> new AccessibleSkillView(
                        skill.skillId(), skill.skillName(), skill.label(), skill.description()))
                .toList();
    }

    private List<AgentPolicySkill> boundSkills(String agentId) {
        return catalogRepository.findBoundSkills(agentId).stream()
                .sorted(Comparator.comparing(AgentPolicySkill::skillId))
                .toList();
    }

    private SkillUserPolicyView toPolicyView(
            AgentPolicySkill skill,
            SkillUserPolicy policy,
            List<SkillUserAccessRule> users) {
        return new SkillUserPolicyView(
                skill.skillId(),
                skill.skillName(),
                skill.label(),
                skill.description(),
                policy == null ? AccessScope.PUBLIC : policy.effectiveAccessScope(),
                users == null ? List.of() : users.stream()
                        .sorted(Comparator.comparing(SkillUserAccessRule::userId))
                        .toList());
    }

    private AccessScope effectiveScope(AccessScope scope) {
        return scope == null ? AccessScope.PUBLIC : scope;
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
        return new ApiException(
                ErrorCode.POLICY_STORE_UNAVAILABLE,
                "policy store is unavailable",
                exception);
    }
}
