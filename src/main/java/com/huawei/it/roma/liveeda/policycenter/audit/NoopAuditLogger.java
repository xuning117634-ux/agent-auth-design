package com.huawei.it.roma.liveeda.policycenter.audit;

import java.util.Map;

public final class NoopAuditLogger implements AuditLogger {

    public static final NoopAuditLogger INSTANCE = new NoopAuditLogger();

    private NoopAuditLogger() {
    }

    @Override
    public void record(String eventType, Map<String, String> fields) {
        // Used by unit tests that do not assert audit side effects.
    }
}
