package com.huawei.it.roma.liveeda.policycenter.domain;

public enum AuthMode {
    NO_AUTH_REQUIRED,
    USER_AUTH_REQUIRED,
    PER_CALL_AUTH_REQUIRED;

    public static AuthMode normalize(AuthMode authMode) {
        return authMode == null ? USER_AUTH_REQUIRED : authMode;
    }
}
