package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.util.List;

public record AccessibleSkillListResponse(
        String agentId,
        String userId,
        List<AccessibleSkillItemResponse> skills) {
}
