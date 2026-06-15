package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolAuthorizationPrecheckItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolAuthorizationPrecheckRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolAuthorizationPrecheckResponse;
import com.huawei.it.roma.liveeda.policycenter.service.ToolAuthorizationPrecheckResult;
import com.huawei.it.roma.liveeda.policycenter.service.ToolAuthorizationPrecheckService;
import com.huawei.it.roma.liveeda.policycenter.service.ToolAuthorizationPrecheckTool;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ToolAuthorizationPrecheckController {

    private final ToolAuthorizationPrecheckService service;

    public ToolAuthorizationPrecheckController(ToolAuthorizationPrecheckService service) {
        this.service = service;
    }

    @PostMapping("/internal/tool-authorization-prechecks")
    ResponseEntity<ToolAuthorizationPrecheckResponse> precheck(
            @RequestHeader(value = "tokenid", required = false) String tokenid,
            @Valid @RequestBody ToolAuthorizationPrecheckRequest request) {
        ToolAuthorizationPrecheckResult result = service.precheck(
                tokenid,
                request.tools().stream()
                        .map(tool -> new ToolAuthorizationPrecheckTool(tool.serverId(), tool.toolName()))
                        .toList());
        HttpStatus status = result.tools().isEmpty() ? HttpStatus.OK : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(new ToolAuthorizationPrecheckResponse(
                        result.tokenid(),
                        result.tools().stream()
                                .map(tool -> new ToolAuthorizationPrecheckItemResponse(
                                        tool.serverName(),
                                        tool.toolName(),
                                        tool.toolId(),
                                        tool.decision()))
                                .toList()));
    }
}
