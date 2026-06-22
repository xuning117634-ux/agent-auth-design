package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import com.huawei.it.roma.liveeda.policycenter.domain.SkillUserAccessRule;

import java.util.List;

public record SkillUserPolicyView(
        String skillId,
        String skillName,
        String label,
        String description,
        AccessScope accessScope,
        List<SkillUserAccessRule> users) {
}
