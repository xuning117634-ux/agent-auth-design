package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.util.List;

public record AccessibleToolListResponse(String agentId, String userId, List<AccessibleToolItemResponse> tools) {
}
