package com.huawei.it.roma.liveeda.policycenter.service;

@FunctionalInterface
public interface ToolUserPolicyEvaluator {

    boolean canAccessTool(String agentId, String toolId, String userId);

    static ToolUserPolicyEvaluator allowAll() {
        return (agentId, toolId, userId) -> true;
    }
}
