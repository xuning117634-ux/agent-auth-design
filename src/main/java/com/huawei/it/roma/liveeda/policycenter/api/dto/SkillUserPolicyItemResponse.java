package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;

import java.util.List;

public record SkillUserPolicyItemResponse(
        String skillId,
        String skillName,
        String label,
        String description,
        AccessScope accessScope,
        List<UserPolicyItemResponse> users) {
}
