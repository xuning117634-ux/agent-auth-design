package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.time.Instant;
import java.util.List;

public record ToolPolicyListResponse(String agentId, List<ToolPolicyItemResponse> tools, Instant updatedAt) {
}
