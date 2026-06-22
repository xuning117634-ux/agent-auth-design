package com.huawei.it.roma.liveeda.policycenter.api.dto;

import com.huawei.it.roma.liveeda.policycenter.domain.AccessScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SkillUserPolicyItemRequest(
        @NotBlank String skillId,
        AccessScope accessScope,
        @NotNull @Valid List<UserPolicyItemRequest> users) {
}
