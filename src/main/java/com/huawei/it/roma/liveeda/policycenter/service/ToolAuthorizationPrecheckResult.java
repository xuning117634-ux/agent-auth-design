package com.huawei.it.roma.liveeda.policycenter.service;

import java.util.List;

public record ToolAuthorizationPrecheckResult(
        String tokenid,
        List<ToolAuthorizationRequiredTool> tools) {
}
