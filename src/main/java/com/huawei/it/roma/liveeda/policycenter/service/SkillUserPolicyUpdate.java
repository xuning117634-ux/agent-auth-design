package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.util.List;

public record SkillUserPolicyUpdate(
        String skillId,
        AccessScope accessScope,
        List<UserAccessUpdate> users) {
}
