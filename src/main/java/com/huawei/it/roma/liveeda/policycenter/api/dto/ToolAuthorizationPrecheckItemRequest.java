package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ToolAuthorizationPrecheckItemRequest(
        @JsonAlias("serverid")
        @NotBlank
        @Size(max = 128)
        String serverId,

        @JsonAlias("toolname")
        @NotBlank
        @Size(max = 255)
        String toolName) {
}
