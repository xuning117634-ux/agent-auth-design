package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;

import java.time.Instant;
import java.util.List;

public record UserPolicyView(
        String agentId,
        AccessScope accessScope,
        List<UserAccessRule> agentUsers,
        List<ToolUserPolicyView> tools,
        Instant updatedAt) {
}
