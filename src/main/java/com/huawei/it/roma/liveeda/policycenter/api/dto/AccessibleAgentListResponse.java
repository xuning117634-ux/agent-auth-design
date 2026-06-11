package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.util.List;

public record AccessibleAgentListResponse(String userId, List<AccessibleAgentItemResponse> agents) {
}
