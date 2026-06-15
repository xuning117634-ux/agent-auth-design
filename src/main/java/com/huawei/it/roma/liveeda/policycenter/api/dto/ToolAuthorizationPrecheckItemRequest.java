package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record ToolAuthorizationPrecheckItemRequest(
        @JsonAlias("serverid")
        @NotBlank
        String serverId,

        @JsonAlias("toolname")
        @NotBlank
        String toolName) {
}
