package com.huawei.it.roma.liveeda.policycenter.api.filter;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TraceIdFilterTest {

    @Test
    void usesIncomingTraceIdAndWritesResponseHeader() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ProbeController())
                .addFilters(new TraceIdFilter())
                .build();

        mockMvc.perform(get("/probe").header("X-Trace-Id", "trace-abc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-abc"));

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void generatesTraceIdWhenHeaderIsMissing() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ProbeController())
                .addFilters(new TraceIdFilter())
                .build();

        mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", not(isEmptyOrNullString())));

        assertThat(MDC.get("traceId")).isNull();
    }

    @RestController
    private static final class ProbeController {

        @GetMapping("/probe")
        void probe(HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
