package com.huawei.it.roma.liveeda.policycenter.domain;

public record AgentAccessDecision(String agentId, String userId, boolean allowed, AgentAccessReason reason) {

    public static AgentAccessDecision allow(String agentId, String userId, AgentAccessReason reason) {
        return new AgentAccessDecision(agentId, userId, true, reason);
    }

    public static AgentAccessDecision deny(String agentId, String userId, AgentAccessReason reason) {
        return new AgentAccessDecision(agentId, userId, false, reason);
    }
}
