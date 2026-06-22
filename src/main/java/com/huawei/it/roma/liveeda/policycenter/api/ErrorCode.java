package com.huawei.it.roma.liveeda.policycenter.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    TOOL_NOT_BOUND(HttpStatus.CONFLICT),
    SKILL_NOT_BOUND(HttpStatus.CONFLICT),
    AUTHORIZATION_NOT_REQUIRED(HttpStatus.CONFLICT),
    POLICY_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    AUTHORIZATION_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
