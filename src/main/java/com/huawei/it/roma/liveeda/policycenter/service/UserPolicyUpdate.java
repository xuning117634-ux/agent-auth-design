package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.util.List;

public record UserPolicyUpdate(
        AccessScope accessScope,
        List<UserAccessUpdate> agentUsers,
        List<ToolUserPolicyUpdate> tools) {
}
