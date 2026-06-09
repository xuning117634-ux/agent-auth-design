package com.huawei.it.roma.liveeda.policycenter.service;

import java.time.Instant;

public record ToolPolicySaveResult(String agentId, int toolCount, Instant updatedAt) {
}
