package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ToolAuthorizationPrecheckRequest(
        @Valid
        @NotEmpty
        @Size(max = 100)
        List<ToolAuthorizationPrecheckItemRequest> tools) {
}
