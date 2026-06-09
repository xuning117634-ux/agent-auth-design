package com.huawei.it.roma.liveeda.policycenter.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Slf4jAuditLogger implements AuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("policy-center-audit");

    @Override
    public void record(String eventType, Map<String, String> fields) {
        LOGGER.info("eventType={} fields={}", eventType, fields);
    }
}
