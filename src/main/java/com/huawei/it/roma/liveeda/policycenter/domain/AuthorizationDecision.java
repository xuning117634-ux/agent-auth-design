package com.huawei.it.roma.liveeda.policycenter.domain;

public record AuthorizationDecision(Decision decision, DecisionReason reason) {

    public static AuthorizationDecision allow(DecisionReason reason) {
        return new AuthorizationDecision(Decision.ALLOW, reason);
    }

    public static AuthorizationDecision authorizationRequired() {
        return new AuthorizationDecision(Decision.AUTHORIZATION_REQUIRED, DecisionReason.USER_AUTHORIZATION_REQUIRED);
    }

    public static AuthorizationDecision authorizationRequired(DecisionReason reason) {
        return new AuthorizationDecision(Decision.AUTHORIZATION_REQUIRED, reason);
    }

    public static AuthorizationDecision deny(DecisionReason reason) {
        return new AuthorizationDecision(Decision.DENY, reason);
    }
}
