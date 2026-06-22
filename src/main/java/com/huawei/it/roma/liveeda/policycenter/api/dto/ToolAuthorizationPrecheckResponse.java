package com.huawei.it.roma.liveeda.policycenter.api.dto;

import java.util.List;

public record ToolAuthorizationPrecheckResponse(
        String tokenid,
        List<ToolAuthorizationPrecheckItemResponse> tools) {
}
