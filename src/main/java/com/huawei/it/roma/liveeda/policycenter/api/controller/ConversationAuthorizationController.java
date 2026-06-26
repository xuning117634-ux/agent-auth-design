package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.dto.BatchConversationAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.BatchConversationAuthorizationResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.CleanupAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ConversationAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ConversationAuthorizationResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ConversationAuthorizationStatusResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ExternalCleanupAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationStatus;
import com.huawei.it.roma.liveeda.policycenter.service.BatchConversationAuthorizationResult;
import com.huawei.it.roma.liveeda.policycenter.service.CleanupResult;
import com.huawei.it.roma.liveeda.policycenter.service.ConversationAuthorizationResult;
import com.huawei.it.roma.liveeda.policycenter.service.ConversationAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversationAuthorizationController {

    private final ConversationAuthorizationService service;

    public ConversationAuthorizationController(ConversationAuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/internal/conversation-authorizations")
    ConversationAuthorizationResponse authorize(@Valid @RequestBody ConversationAuthorizationRequest request) {
        ConversationAuthorizationResult result = service.authorize(
                request.tokenId(),
                request.toolId(),
                request.expiresInSeconds());
        return new ConversationAuthorizationResponse(result.status(), result.toolId());
    }

    @PostMapping("/internal/conversation-authorizations/batch")
    BatchConversationAuthorizationResponse authorizeBatch(
            @RequestHeader(value = AgentGatewayHeaders.ACCESS_TOKEN, required = false) String accessToken,
            @RequestHeader(value = AgentGatewayHeaders.LEGACY_TOKEN_ID, required = false) String legacyTokenId,
            @Valid @RequestBody BatchConversationAuthorizationRequest request) {
        BatchConversationAuthorizationResult result = service.authorizeBatch(
                AgentGatewayHeaders.resolveTokenId(accessToken, legacyTokenId),
                request.toolIds(),
                request.expiresInSeconds());
        return new BatchConversationAuthorizationResponse(
                result.status(),
                result.toolCount(),
                result.toolIds());
    }

    @PostMapping("/internal/conversation-authorizations/status")
    ConversationAuthorizationStatusResponse status(@Valid @RequestBody ConversationAuthorizationRequest request) {
        AuthorizationStatus status = service.status(request.tokenId(), request.toolId());
        return new ConversationAuthorizationStatusResponse(status);
    }

    @PostMapping("/internal/conversation-authorizations/cleanup")
    CleanupResult cleanup(@Valid @RequestBody CleanupAuthorizationRequest request) {
        return service.cleanup(request.tokenId());
    }

    @PostMapping("/external/conversation-authorizations/cleanup")
    CleanupResult cleanupExternal(@Valid @RequestBody ExternalCleanupAuthorizationRequest request) {
        return service.cleanup(request.agentId(), request.userId(), request.conversationId());
    }
}
