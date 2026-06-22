package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UserIdBatchParser {

    private UserIdBatchParser() {
    }

    public static List<String> parse(List<String> values) {
        if (values == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "users must not be null");
        }
        Set<String> userIds = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "userId must not be blank");
            }
            List<String> parsed = Arrays.stream(value.split("[,;，；\\r\\n]+"))
                    .map(String::trim)
                    .filter(userId -> !userId.isEmpty())
                    .toList();
            if (parsed.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "userId must contain at least one valid value");
            }
            userIds.addAll(parsed);
        }
        return List.copyOf(userIds);
    }
}
