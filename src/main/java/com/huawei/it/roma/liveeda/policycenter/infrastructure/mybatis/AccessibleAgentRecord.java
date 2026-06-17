package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

public class AccessibleAgentRecord {
    private String agentId;
    private String accessScope;
    private Boolean whitelisted;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(String accessScope) {
        this.accessScope = accessScope;
    }

    public Boolean getWhitelisted() {
        return whitelisted;
    }

    public void setWhitelisted(Boolean whitelisted) {
        this.whitelisted = whitelisted;
    }
}
