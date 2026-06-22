package com.huawei.it.roma.liveeda.policycenter.infrastructure.redis;

import com.huawei.it.roma.liveeda.policycenter.store.ConversationAuthorizationStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RedisConversationAuthorizationStore implements ConversationAuthorizationStore {

    private final StringRedisTemplate redisTemplate;
    private final long scanCount;

    public RedisConversationAuthorizationStore(
            StringRedisTemplate redisTemplate,
            @Value("${policy-center.redis.scan-count:500}") long scanCount) {
        this.redisTemplate = redisTemplate;
        this.scanCount = scanCount;
    }

    @Override
    public boolean exists(String tokenId, String toolId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId, toolId)));
    }

    @Override
    public boolean consume(String tokenId, String toolId) {
        return Boolean.TRUE.equals(redisTemplate.delete(key(tokenId, toolId)));
    }

    @Override
    public void authorize(String tokenId, String toolId, Duration ttl) {
        redisTemplate.opsForValue().set(key(tokenId, toolId), "1", ttl);
    }

    @Override
    public long cleanup(String tokenId) {
        String match = key(tokenId, "*");
        return redisTemplate.execute((RedisCallback<Long>) connection -> scanAndDelete(connection, match));
    }

    private Long scanAndDelete(RedisConnection connection, String match) {
        RedisKeyCommands keyCommands = connection;
        long deleted = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(match)
                .count(scanCount)
                .build();
        List<byte[]> batch = new ArrayList<>();
        try (Cursor<byte[]> cursor = keyCommands.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= scanCount) {
                    deleted += deleteBatch(keyCommands, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                deleted += deleteBatch(keyCommands, batch);
            }
        }
        return deleted;
    }

    private Long deleteBatch(RedisKeyCommands keyCommands, List<byte[]> batch) {
        Long deleted = keyCommands.del(batch.toArray(byte[][]::new));
        return deleted == null ? 0 : deleted;
    }

    private String key(String tokenId, String toolId) {
        return "authz:{" + tokenId + "}:" + toolId;
    }
}
