package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.util.List;

public record ToolUserPolicyUpdate(
        String toolId,
        AccessScope accessScope,
        List<UserAccessUpdate> users) {
}
