package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.UserAccessRule;

import java.util.List;

public record ToolUserPolicyView(
        String toolId,
        AccessScope accessScope,
        List<UserAccessRule> users) {
}
