package com.huawei.it.roma.liveeda.policycenter.domain;

public record AgentPolicyTool(
        String agentId,
        String serviceId,
        String serviceName,
        String toolName,
        String toolId) {

    public String effectiveServerName() {
        return serviceName == null || serviceName.isBlank() ? serviceId : serviceName;
    }
}
