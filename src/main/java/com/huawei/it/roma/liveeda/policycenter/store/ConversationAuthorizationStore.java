package com.huawei.it.roma.liveeda.policycenter.store;

import java.time.Duration;

public interface ConversationAuthorizationStore {

    boolean exists(String tokenId, String toolId);

    boolean consume(String tokenId, String toolId);

    void authorize(String tokenId, String toolId, Duration ttl);

    long cleanup(String tokenId);
}
