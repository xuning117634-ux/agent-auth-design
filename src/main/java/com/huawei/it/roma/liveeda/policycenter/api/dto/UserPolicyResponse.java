package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.time.Instant;
import java.util.List;

public record UserPolicyResponse(
        String agentId,
        AccessScope accessScope,
        List<UserPolicyItemResponse> agentUsers,
        List<ToolUserPolicyItemResponse> tools,
        Instant updatedAt) {
}
