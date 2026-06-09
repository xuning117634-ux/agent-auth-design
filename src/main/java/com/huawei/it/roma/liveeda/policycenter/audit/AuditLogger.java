package com.huawei.it.roma.liveeda.policycenter.audit;

import java.util.Map;

public interface AuditLogger {

    void record(String eventType, Map<String, String> fields);
}
