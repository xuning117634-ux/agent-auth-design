package com.huawei.it.roma.liveeda.policycenter.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequestBodySizeLimitFilterTest {

    @Test
    void rejectsOversizedToolPrecheckBodyBeforeController() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ProbeController())
                .addFilters(
                        new TraceIdFilter(),
                        new RequestBodySizeLimitFilter(new ObjectMapper(), 32))
                .build();

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .header("X-Trace-Id", "trace-large-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tools": [
                                    {"serverId": "finance-server", "toolName": "quoteQuery"}
                                  ]
                                }
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().string("X-Trace-Id", "trace-large-body"))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("request body is too large"))
                .andExpect(jsonPath("$.traceId").value("trace-large-body"));
    }

    @Test
    void allowsOtherPathsEvenWhenBodyIsLarge() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ProbeController())
                .addFilters(new RequestBodySizeLimitFilter(new ObjectMapper(), 1))
                .build();

        mockMvc.perform(post("/other")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"large-enough\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void allowsPrecheckWhenBodyIsWithinLimit() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ProbeController())
                .addFilters(new RequestBodySizeLimitFilter(new ObjectMapper(), 1024))
                .build();

        mockMvc.perform(post("/internal/tool-authorization-prechecks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @RestController
    private static final class ProbeController {

        @PostMapping({"/internal/tool-authorization-prechecks", "/other"})
        String probe() {
            return "{\"status\":\"ok\"}";
        }
    }
}
