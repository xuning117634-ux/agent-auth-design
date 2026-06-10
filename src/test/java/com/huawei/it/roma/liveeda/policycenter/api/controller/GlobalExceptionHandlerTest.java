package com.huawei.it.roma.liveeda.policycenter.api.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.GlobalExceptionHandler;
import com.huawei.it.roma.liveeda.policycenter.api.filter.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void apiExceptionWithCauseLogsContextAndStackTrace() throws Exception {
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        mockMvc.perform(get("/api-fail").header("X-Trace-Id", "trace-api"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Trace-Id", "trace-api"))
                .andExpect(jsonPath("$.code").value("POLICY_STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value("trace-api"));

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(
                    "API_EXCEPTION",
                    "traceId=trace-api",
                    "errorCode=POLICY_STORE_UNAVAILABLE",
                    "operation=LIST_TOOL_POLICIES",
                    "agentId=agent-a");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("policy store is unavailable");
            assertThat(event.getThrowableProxy().getCause().getMessage()).isEqualTo("database unavailable");
        });
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @RestController
    private static final class FailingController {

        @GetMapping("/explode")
        void explode() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/api-fail")
        void apiFail() {
            throw new ApiException(
                    ErrorCode.POLICY_STORE_UNAVAILABLE,
                    "policy store is unavailable",
                    new IllegalStateException("database unavailable"),
                    Map.of("operation", "LIST_TOOL_POLICIES", "agentId", "agent-a"));
        }
    }
}
