package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.util.List;

public record ToolAuthorizationPrecheckResponse(
        List<ToolAuthorizationPrecheckItemResponse> tools) {
}
