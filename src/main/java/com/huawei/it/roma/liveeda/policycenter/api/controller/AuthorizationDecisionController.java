package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.dto.AuthorizationDecisionRequest;
import com.huawei.it.roma.liveeda.policycenter.domain.AuthorizationDecision;
import com.huawei.it.roma.liveeda.policycenter.service.AuthorizationDecisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthorizationDecisionController {

    private final AuthorizationDecisionService service;

    public AuthorizationDecisionController(AuthorizationDecisionService service) {
        this.service = service;
    }

    @PostMapping("/internal/authorization-decisions")
    AuthorizationDecision decide(@Valid @RequestBody AuthorizationDecisionRequest request) {
        return service.decide(request.tokenId(), request.toolId());
    }
}
