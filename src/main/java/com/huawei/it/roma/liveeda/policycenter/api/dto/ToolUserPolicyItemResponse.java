package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.util.List;

public record ToolUserPolicyItemResponse(
        String toolId,
        AccessScope accessScope,
        List<UserPolicyItemResponse> users) {
}
