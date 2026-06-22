package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.dto.BatchConversationAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.CleanupAuthorizationRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ConversationAuthorizationRequest;
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
    ConversationAuthorizationResult authorize(@Valid @RequestBody ConversationAuthorizationRequest request) {
        return service.authorize(request.tokenId(), request.toolId(), request.expiresInSeconds());
    }

    @PostMapping("/internal/conversation-authorizations/batch")
    BatchConversationAuthorizationResult authorizeBatch(
            @RequestHeader(value = "tokenid", required = false) String tokenid,
            @Valid @RequestBody BatchConversationAuthorizationRequest request) {
        return service.authorizeBatch(tokenid, request.toolIds(), request.expiresInSeconds());
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
