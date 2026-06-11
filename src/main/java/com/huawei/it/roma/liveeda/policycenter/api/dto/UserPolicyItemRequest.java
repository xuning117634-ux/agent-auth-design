package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UserPolicyItemRequest(@NotBlank String userId) {
}
