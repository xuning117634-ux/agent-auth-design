package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessDecision;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentAccessReason;
import com.huawei.it.roma.liveeda.policycenter.domain.AgentUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserAccessRule;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolUserPolicy;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisUserPolicyRepositoryTest {

    @Test
    void mapsAccessibleAgentsFromMapperRows() {
        FakeUserPolicyMapper mapper = new FakeUserPolicyMapper();
        mapper.accessibleAgents = List.of(
                accessibleAgent("agent-a", AccessScope.PUBLIC, false),
                accessibleAgent("agent-b", AccessScope.RESTRICTED, true));
        MyBatisUserPolicyRepository repository = new MyBatisUserPolicyRepository(mapper);

        assertThat(repository.findAccessibleAgents("user-42"))
                .containsExactly(
                        AgentAccessDecision.allow(
                                "agent-a",
                                "user-42",
                                AgentAccessReason.AGENT_PUBLIC_ACCESS),
                        AgentAccessDecision.allow(
                                "agent-b",
                                "user-42",
                        AgentAccessReason.AGENT_USER_WHITELISTED));
    }

    @Test
    void mapsPolicyQueriesFromMapperRecords() {
        FakeUserPolicyMapper mapper = new FakeUserPolicyMapper();
        mapper.agentPolicy = agentPolicy("agent-a", AccessScope.RESTRICTED);
        mapper.toolPolicies = List.of(
                toolPolicy("agent-a", "tool-a", AccessScope.PUBLIC),
                toolPolicy("agent-a", "tool-b", AccessScope.RESTRICTED));
        mapper.toolPolicy = toolPolicy("agent-a", "tool-b", AccessScope.RESTRICTED);
        mapper.agentRules = List.of(agentRule("agent-a", "user-42"));
        mapper.toolRules = List.of(toolRule("agent-a", "tool-b", "user-42"));
        mapper.agentUserCount = 1;
        mapper.toolUserCount = 1;
        MyBatisUserPolicyRepository repository = new MyBatisUserPolicyRepository(mapper);

        assertThat(repository.findAgentPolicy("agent-a"))
                .contains(new AgentUserPolicy("agent-a", AccessScope.RESTRICTED, null));
        assertThat(repository.findToolPolicies("agent-a"))
                .containsExactly(
                        new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC, null),
                        new ToolUserPolicy("agent-a", "tool-b", AccessScope.RESTRICTED, null));
        assertThat(repository.findToolPolicy("agent-a", "tool-b"))
                .contains(new ToolUserPolicy("agent-a", "tool-b", AccessScope.RESTRICTED, null));
        assertThat(repository.findAgentUserRules("agent-a"))
                .containsExactly(new UserAccessRule("agent-a", "user-42", null));
        assertThat(repository.findToolUserRules("agent-a"))
                .containsExactly(new ToolUserAccessRule("agent-a", "tool-b", "user-42", null));
        assertThat(repository.existsAgentUser("agent-a", "user-42")).isTrue();
        assertThat(repository.existsToolUser("agent-a", "tool-b", "user-42")).isTrue();
    }

    @Test
    void replaceAllDeletesOldRowsAndInsertsNewRows() {
        FakeUserPolicyMapper mapper = new FakeUserPolicyMapper();
        MyBatisUserPolicyRepository repository = new MyBatisUserPolicyRepository(mapper);

        repository.replaceAll(
                "agent-a",
                new AgentUserPolicy("agent-a", AccessScope.RESTRICTED),
                List.of(new ToolUserPolicy("agent-a", "tool-a", AccessScope.PUBLIC)),
                List.of(new UserAccessRule("agent-a", "user-42")),
                List.of(new ToolUserAccessRule("agent-a", "tool-a", "user-42")));

        assertThat(mapper.operations)
                .containsExactly(
                        "upsertAgentPolicy:agent-a:RESTRICTED",
                        "deleteAgentUserRules:agent-a",
                        "deleteToolUserRules:agent-a",
                        "deleteToolPolicies:agent-a",
                        "insertToolPolicy:agent-a:tool-a:PUBLIC",
                        "insertAgentUserRule:agent-a:user-42",
                        "insertToolUserRule:agent-a:tool-a:user-42");
    }

    private static AccessibleAgentRecord accessibleAgent(
            String agentId,
            AccessScope accessScope,
            boolean whitelisted) {
        AccessibleAgentRecord record = new AccessibleAgentRecord();
        record.setAgentId(agentId);
        record.setAccessScope(accessScope.name());
        record.setWhitelisted(whitelisted);
        assertThat(record.getWhitelisted()).isEqualTo(whitelisted);
        return record;
    }

    private static AgentUserPolicyRecord agentPolicy(String agentId, AccessScope accessScope) {
        AgentUserPolicyRecord record = new AgentUserPolicyRecord();
        record.setAgentId(agentId);
        record.setAccessScope(accessScope.name());
        assertThat(record.getCreatedAt()).isNull();
        return record;
    }

    private static ToolUserPolicyRecord toolPolicy(String agentId, String toolId, AccessScope accessScope) {
        ToolUserPolicyRecord record = new ToolUserPolicyRecord();
        record.setAgentId(agentId);
        record.setToolId(toolId);
        record.setAccessScope(accessScope.name());
        assertThat(record.getCreatedAt()).isNull();
        return record;
    }

    private static UserAccessPolicyRecord agentRule(String agentId, String userId) {
        UserAccessPolicyRecord record = new UserAccessPolicyRecord();
        record.setAgentId(agentId);
        record.setUserId(userId);
        assertThat(record.getCreatedAt()).isNull();
        return record;
    }

    private static ToolUserAccessPolicyRecord toolRule(String agentId, String toolId, String userId) {
        ToolUserAccessPolicyRecord record = new ToolUserAccessPolicyRecord();
        record.setAgentId(agentId);
        record.setToolId(toolId);
        record.setUserId(userId);
        assertThat(record.getCreatedAt()).isNull();
        return record;
    }

    private static final class FakeUserPolicyMapper implements UserPolicyMapper {
        private AgentUserPolicyRecord agentPolicy;
        private ToolUserPolicyRecord toolPolicy;
        private List<ToolUserPolicyRecord> toolPolicies = List.of();
        private List<UserAccessPolicyRecord> agentRules = List.of();
        private List<ToolUserAccessPolicyRecord> toolRules = List.of();
        private List<AccessibleAgentRecord> accessibleAgents = List.of();
        private int agentUserCount;
        private int toolUserCount;
        private final List<String> operations = new ArrayList<>();

        @Override
        public AgentUserPolicyRecord selectAgentPolicy(String agentId) {
            return agentPolicy;
        }

        @Override
        public List<ToolUserPolicyRecord> selectToolPolicies(String agentId) {
            return toolPolicies;
        }

        @Override
        public ToolUserPolicyRecord selectToolPolicy(String agentId, String toolId) {
            return toolPolicy;
        }

        @Override
        public List<UserAccessPolicyRecord> selectAgentUserRules(String agentId) {
            return agentRules;
        }

        @Override
        public List<ToolUserAccessPolicyRecord> selectToolUserRules(String agentId) {
            return toolRules;
        }

        @Override
        public int countAgentUser(String agentId, String userId) {
            return agentUserCount;
        }

        @Override
        public int countToolUser(String agentId, String toolId, String userId) {
            return toolUserCount;
        }

        @Override
        public List<AccessibleAgentRecord> selectAccessibleAgents(String userId) {
            return accessibleAgents;
        }

        @Override
        public void upsertAgentPolicy(AgentUserPolicyRecord record) {
            operations.add("upsertAgentPolicy:%s:%s".formatted(record.getAgentId(), record.getAccessScope()));
        }

        @Override
        public void insertToolPolicy(ToolUserPolicyRecord record) {
            operations.add("insertToolPolicy:%s:%s:%s".formatted(
                    record.getAgentId(),
                    record.getToolId(),
                    record.getAccessScope()));
        }

        @Override
        public void insertAgentUserRule(UserAccessPolicyRecord record) {
            operations.add("insertAgentUserRule:%s:%s".formatted(record.getAgentId(), record.getUserId()));
        }

        @Override
        public void insertToolUserRule(ToolUserAccessPolicyRecord record) {
            operations.add("insertToolUserRule:%s:%s:%s".formatted(
                    record.getAgentId(),
                    record.getToolId(),
                    record.getUserId()));
        }

        @Override
        public void deleteToolPolicies(String agentId) {
            operations.add("deleteToolPolicies:" + agentId);
        }

        @Override
        public void deleteAgentUserRules(String agentId) {
            operations.add("deleteAgentUserRules:" + agentId);
        }

        @Override
        public void deleteToolUserRules(String agentId) {
            operations.add("deleteToolUserRules:" + agentId);
        }
    }
}
