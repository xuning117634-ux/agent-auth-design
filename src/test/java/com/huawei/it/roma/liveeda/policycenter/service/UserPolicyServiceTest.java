package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.repository.UserPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPolicyServiceTest {

    @Test
    void rejectsToolUserRulesForUnboundTools() {
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(new FakeUserPolicyRepository(), toolPolicies);

        assertThatThrownBy(() -> service.replacePolicy("agent-a", new UserPolicyUpdate(
                AccessScope.PUBLIC,
                List.of(),
                List.of(new ToolUserPolicyUpdate(
                        "tool-z",
                        AccessScope.RESTRICTED,
                        List.of(new UserAccessUpdate("user-42")))))))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo(ErrorCode.TOOL_NOT_BOUND));
    }

    @Test
    void missingAgentAndToolPoliciesDefaultToPublic() {
        FakeUserPolicyRepository userPolicies = new FakeUserPolicyRepository();
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED),
                new ToolPolicy("agent-a", "tool-b", AuthMode.USER_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(userPolicies, toolPolicies);

        assertThat(service.decideAgentAccess("agent-a", "user-99").allowed()).isTrue();
        assertThat(service.decideAgentAccess("agent-a", "user-99").reason())
                .isEqualTo(AgentAccessReason.AGENT_PUBLIC_ACCESS);
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-99")).isTrue();
        assertThat(service.listPolicy("agent-a").tools())
                .extracting(ToolUserPolicyView::toolId)
                .containsExactly("tool-a", "tool-b");
        assertThat(service.listPolicy("agent-a").tools())
                .extracting(ToolUserPolicyView::accessScope)
                .containsOnly(AccessScope.PUBLIC);
    }

    @Test
    void publicScopesIgnoreSavedWhitelists() {
        FakeUserPolicyRepository userPolicies = new FakeUserPolicyRepository();
        userPolicies.agentPolicy = Optional.of(new AgentUserPolicy("agent-a", AccessScope.PUBLIC));
        userPolicies.agentRules.add(new UserAccessRule("agent-a", "user-42"));
        userPolicies.toolPolicies.add(new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC));
        userPolicies.toolRules.add(new ToolUserAccessRule("agent-a", "tool-a", "user-42"));
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(userPolicies, toolPolicies);

        assertThat(service.decideAgentAccess("agent-a", "user-99").allowed()).isTrue();
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-99")).isTrue();
    }

    @Test
    void restrictedScopesOnlyAllowWhitelistedUsers() {
        FakeUserPolicyRepository userPolicies = new FakeUserPolicyRepository();
        userPolicies.agentPolicy = Optional.of(new AgentUserPolicy("agent-a", AccessScope.RESTRICTED));
        userPolicies.agentRules.add(new UserAccessRule("agent-a", "user-42"));
        userPolicies.toolPolicies.add(new ToolUserPolicy("agent-a", "tool-a", AccessScope.RESTRICTED));
        userPolicies.toolPolicies.add(new ToolUserPolicy("agent-a", "tool-b", AccessScope.RESTRICTED));
        userPolicies.toolRules.add(new ToolUserAccessRule("agent-a", "tool-a", "user-42"));
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED),
                new ToolPolicy("agent-a", "tool-b", AuthMode.USER_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(userPolicies, toolPolicies);

        assertThat(service.decideAgentAccess("agent-a", "user-42").reason())
                .isEqualTo(AgentAccessReason.AGENT_USER_WHITELISTED);
        assertThat(service.decideAgentAccess("agent-a", "user-99").reason())
                .isEqualTo(AgentAccessReason.AGENT_USER_NOT_WHITELISTED);
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-42")).isTrue();
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-99")).isFalse();
        assertThat(service.canAccessTool("agent-a", "tool-b", "user-42")).isFalse();
    }

    @Test
    void restrictedAgentDoesNotAffectToolAccess() {
        FakeUserPolicyRepository userPolicies = new FakeUserPolicyRepository();
        userPolicies.agentPolicy = Optional.of(new AgentUserPolicy("agent-a", AccessScope.RESTRICTED));
        userPolicies.toolPolicies.add(new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC));
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(userPolicies, toolPolicies);

        assertThat(service.decideAgentAccess("agent-a", "user-99").allowed()).isFalse();
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-99")).isTrue();
    }

    @Test
    void replacePolicyKeepsWhitelistForPublicScopes() {
        FakeUserPolicyRepository repository = new FakeUserPolicyRepository();
        FakeToolPolicyRepository toolPolicies = FakeToolPolicyRepository.withPolicies(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED),
                new ToolPolicy("agent-a", "tool-b", AuthMode.USER_AUTH_REQUIRED));
        UserPolicyService service = new UserPolicyService(repository, toolPolicies);

        service.replacePolicy("agent-a", new UserPolicyUpdate(
                AccessScope.PUBLIC,
                List.of(new UserAccessUpdate("user-42")),
                List.of(new ToolUserPolicyUpdate(
                        "tool-a",
                        AccessScope.PUBLIC,
                        List.of(new UserAccessUpdate("user-42"))))));

        assertThat(repository.agentPolicy).contains(new AgentUserPolicy("agent-a", AccessScope.PUBLIC));
        assertThat(repository.agentRules).containsExactly(new UserAccessRule("agent-a", "user-42"));
        assertThat(repository.toolPolicies)
                .containsExactly(new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC));
        assertThat(repository.toolRules)
                .containsExactly(new ToolUserAccessRule("agent-a", "tool-a", "user-42"));
        assertThat(service.canAccessTool("agent-a", "tool-a", "user-99")).isTrue();
    }

    private static final class FakeUserPolicyRepository implements UserPolicyRepository {
        private Optional<AgentUserPolicy> agentPolicy = Optional.empty();
        private final List<ToolUserPolicy> toolPolicies = new ArrayList<>();
        private final List<UserAccessRule> agentRules = new ArrayList<>();
        private final List<ToolUserAccessRule> toolRules = new ArrayList<>();

        @Override
        public Optional<AgentUserPolicy> findAgentPolicy(String agentId) {
            return agentPolicy;
        }

        @Override
        public List<ToolUserPolicy> findToolPolicies(String agentId) {
            return toolPolicies;
        }

        @Override
        public Optional<ToolUserPolicy> findToolPolicy(String agentId, String toolId) {
            return toolPolicies.stream()
                    .filter(policy -> policy.agentId().equals(agentId) && policy.toolId().equals(toolId))
                    .findFirst();
        }

        @Override
        public List<UserAccessRule> findAgentUserRules(String agentId) {
            return agentRules;
        }

        @Override
        public List<ToolUserAccessRule> findToolUserRules(String agentId) {
            return toolRules;
        }

        @Override
        public boolean existsAgentUser(String agentId, String userId) {
            return agentRules.stream()
                    .anyMatch(rule -> rule.agentId().equals(agentId) && rule.userId().equals(userId));
        }

        @Override
        public boolean existsToolUser(String agentId, String toolId, String userId) {
            return toolRules.stream()
                    .anyMatch(rule -> rule.agentId().equals(agentId)
                            && rule.toolId().equals(toolId)
                            && rule.userId().equals(userId));
        }

        @Override
        public List<String> findKnownAgentIds() {
            return List.of("agent-a");
        }

        @Override
        public void replaceAll(
                String agentId,
                AgentUserPolicy agentPolicy,
                List<ToolUserPolicy> toolPolicies,
                List<UserAccessRule> agentRules,
                List<ToolUserAccessRule> toolRules) {
            this.agentPolicy = Optional.of(agentPolicy);
            this.toolPolicies.clear();
            this.toolPolicies.addAll(toolPolicies);
            this.agentRules.clear();
            this.agentRules.addAll(agentRules);
            this.toolRules.clear();
            this.toolRules.addAll(toolRules);
        }
    }

    private static final class FakeToolPolicyRepository implements ToolPolicyRepository {
        private final List<ToolPolicy> policies;

        private FakeToolPolicyRepository(List<ToolPolicy> policies) {
            this.policies = policies;
        }

        static FakeToolPolicyRepository withPolicies(ToolPolicy... policies) {
            return new FakeToolPolicyRepository(List.of(policies));
        }

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return policies.stream()
                    .filter(policy -> policy.agentId().equals(agentId) && policy.toolId().equals(toolId))
                    .findFirst();
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            return policies.stream()
                    .filter(policy -> policy.agentId().equals(agentId))
                    .toList();
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            throw new UnsupportedOperationException();
        }
    }
}
