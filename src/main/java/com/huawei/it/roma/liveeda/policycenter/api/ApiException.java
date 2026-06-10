package com.huawei.it.roma.liveeda.policycenter.api;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> context;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.context = Map.of();
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        this(code, message, cause, Map.of());
    }

    public ApiException(ErrorCode code, String message, Throwable cause, Map<String, String> context) {
        super(message, cause);
        this.code = code;
        this.context = context == null || context.isEmpty() ? Map.of() : Map.copyOf(context);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> context() {
        return context;
    }
}
