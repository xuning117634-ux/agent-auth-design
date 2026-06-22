package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthMode;
import com.huawei.it.roma.liveeda.policycenter.domain.ToolPolicy;
import com.huawei.it.roma.liveeda.policycenter.repository.ToolPolicyRepository;
import com.huawei.it.roma.liveeda.policycenter.service.ToolPolicySaveResult;
import com.huawei.it.roma.liveeda.policycenter.service.ToolPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminToolPolicyControllerTest {

    private final CapturingToolPolicyRepository repository = new CapturingToolPolicyRepository();
    private final ToolPolicyService service = new ToolPolicyService(repository);
    private final MockMvc mockMvc = standaloneSetup(new AdminToolPolicyController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(MockMvcSupport.jsonConverter())
            .build();

    @Test
    void replacesToolPolicies() throws Exception {
        mockMvc.perform(put("/admin/agents/agent-a/tool-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {"toolId": "tool-a", "authMode": "NO_AUTH_REQUIRED"},
                                    {"toolId": "tool-per-call", "authMode": "PER_CALL_AUTH_REQUIRED"},
                                    {"toolId": "tool-b"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-a"))
                .andExpect(jsonPath("$.toolCount").value(3))
                .andExpect(jsonPath("$.updatedAt").isString());

        assertThat(repository.saved).containsExactly(
                new ToolPolicy("agent-a", "tool-a", AuthMode.NO_AUTH_REQUIRED),
                new ToolPolicy("agent-a", "tool-per-call", AuthMode.PER_CALL_AUTH_REQUIRED),
                new ToolPolicy("agent-a", "tool-b", AuthMode.USER_AUTH_REQUIRED));
    }

    private static final class CapturingToolPolicyRepository implements ToolPolicyRepository {
        private List<ToolPolicy> saved = new ArrayList<>();

        @Override
        public Optional<ToolPolicy> findByAgentIdAndToolId(String agentId, String toolId) {
            return Optional.empty();
        }

        @Override
        public List<ToolPolicy> findByAgentId(String agentId) {
            return saved;
        }

        @Override
        public void replaceAll(String agentId, List<ToolPolicy> policies) {
            saved = new ArrayList<>(policies);
        }
    }
}
