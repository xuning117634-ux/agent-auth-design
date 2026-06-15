package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.time.Instant;

public record UserPolicyItemResponse(String userId, Instant updatedAt) {
}
