package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveUserPolicyRequest(
        AccessScope accessScope,
        @NotNull @Valid List<UserPolicyItemRequest> agentUsers,
        @NotNull @Valid List<ToolUserPolicyItemRequest> tools) {
}
