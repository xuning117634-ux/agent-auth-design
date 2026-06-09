package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveToolPoliciesRequest(@NotNull @Valid List<ToolPolicyItemRequest> tools) {
}
