package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchConversationAuthorizationRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotBlank @Size(max = 255) String> toolIds,
        Long expiresInSeconds) {
}
