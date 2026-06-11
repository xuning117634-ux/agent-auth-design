package com.huawei.it.roma.liveeda.policycenter.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiExceptionTest {

    @Test
    void keepsCauseAndContext() {
        RuntimeException cause = new RuntimeException("database unavailable");
        ApiException exception = new ApiException(
                ErrorCode.POLICY_STORE_UNAVAILABLE,
                "policy store is unavailable",
                cause,
                Map.of("operation", "LIST_TOOL_POLICIES", "agentId", "agent-a"));

        assertThat(exception.code()).isEqualTo(ErrorCode.POLICY_STORE_UNAVAILABLE);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.context()).containsEntry("operation", "LIST_TOOL_POLICIES")
                .containsEntry("agentId", "agent-a");
    }

    @Test
    void returnsEmptyContextWhenContextIsMissing() {
        ApiException exception = new ApiException(ErrorCode.INVALID_REQUEST, "request is invalid");

        assertThat(exception.context()).isEmpty();
    }

    @Test
    void exposesContextAsReadOnly() {
        ApiException exception = new ApiException(
                ErrorCode.POLICY_STORE_UNAVAILABLE,
                "policy store is unavailable",
                new RuntimeException("database unavailable"),
                Map.of("operation", "LIST_TOOL_POLICIES"));

        assertThatThrownBy(() -> exception.context().put("agentId", "agent-a"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
