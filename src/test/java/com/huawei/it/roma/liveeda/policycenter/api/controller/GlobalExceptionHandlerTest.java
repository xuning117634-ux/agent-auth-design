package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.api.filter.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    void internalErrorReturnsTraceIdHeaderAndBody() throws Exception {
        mockMvc.perform(get("/explode").header("X-Trace-Id", "trace-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Trace-Id", "trace-500"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-500"));
    }

    @RestController
    private static final class FailingController {

        @GetMapping("/explode")
        void explode() {
            throw new IllegalStateException("boom");
        }
    }
}
