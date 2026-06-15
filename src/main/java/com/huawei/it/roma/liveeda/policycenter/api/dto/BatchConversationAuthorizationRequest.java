package com.huawei.it.roma.liveeda.policycenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchConversationAuthorizationRequest(
        @NotEmpty
        List<@NotBlank String> toolIds) {
}
