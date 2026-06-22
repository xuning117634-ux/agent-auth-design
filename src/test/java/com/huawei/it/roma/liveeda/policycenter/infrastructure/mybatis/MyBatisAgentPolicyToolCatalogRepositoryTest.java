package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import com.huawei.it.roma.liveeda.policycenter.domain.AgentPolicyTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisAgentPolicyToolCatalogRepositoryTest {

    @Test
    void mapsBatchBoundToolsFromMapperRows() {
        FakeAgentPolicyToolMapper mapper = new FakeAgentPolicyToolMapper();
        mapper.boundTools = List.of(record(
                "agent-a",
                "crm-service",
                "CRM Service",
                "Customer Query",
                "tool-a"));
        MyBatisAgentPolicyToolCatalogRepository repository = new MyBatisAgentPolicyToolCatalogRepository(mapper);

        assertThat(repository.findBoundTools("agent-a", List.of("tool-a", "tool-b")))
                .containsExactly(new AgentPolicyTool(
                        "agent-a",
                        "crm-service",
                        "CRM Service",
                        "Customer Query",
                        "tool-a"));
        assertThat(mapper.lastToolIds).containsExactly("tool-a", "tool-b");
    }

    @Test
    void emptyToolIdsSkipMapperCall() {
        FakeAgentPolicyToolMapper mapper = new FakeAgentPolicyToolMapper();
        MyBatisAgentPolicyToolCatalogRepository repository = new MyBatisAgentPolicyToolCatalogRepository(mapper);

        assertThat(repository.findBoundTools("agent-a", List.of())).isEmpty();
        assertThat(mapper.selectBoundToolsCalled).isFalse();
    }

    private static AgentPolicyToolRecord record(
            String agentId,
            String serviceId,
            String serviceName,
            String toolName,
            String toolId) {
        AgentPolicyToolRecord record = new AgentPolicyToolRecord();
        record.setAgentId(agentId);
        record.setServiceId(serviceId);
        record.setServiceName(serviceName);
        record.setToolName(toolName);
        record.setToolId(toolId);
        return record;
    }

    private static final class FakeAgentPolicyToolMapper implements AgentPolicyToolMapper {
        private List<AgentPolicyToolRecord> boundTools = List.of();
        private List<String> lastToolIds = List.of();
        private boolean selectBoundToolsCalled;

        @Override
        public AgentPolicyToolRecord selectBoundTool(String agentId, String serviceId, String toolName) {
            return null;
        }

        @Override
        public List<AgentPolicyToolRecord> selectBoundTools(String agentId, List<String> toolIds) {
            selectBoundToolsCalled = true;
            lastToolIds = List.copyOf(toolIds);
            return boundTools;
        }
    }
}
