package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.dto.SaveUserPolicyRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.ToolUserPolicyItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.UserPolicyItemRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.UserPolicyItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.UserPolicyResponse;
import com.huawei.it.roma.liveeda.policycenter.service.ToolUserPolicyUpdate;
import com.huawei.it.roma.liveeda.policycenter.service.UserAccessUpdate;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicySaveResult;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyService;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyUpdate;
import com.huawei.it.roma.liveeda.policycenter.service.UserPolicyView;
import com.huawei.it.roma.liveeda.policycenter.service.UserIdBatchParser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminUserPolicyController {

    private final UserPolicyService service;

    public AdminUserPolicyController(UserPolicyService service) {
        this.service = service;
    }

    @GetMapping("/admin/agents/{agentId}/user-policies")
    UserPolicyResponse list(@PathVariable String agentId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        UserPolicyView view = service.listPolicy(agentId);
        List<UserPolicyItemResponse> agentUsers = view.agentUsers().stream()
                .map(rule -> new UserPolicyItemResponse(rule.userId(), rule.updatedAt()))
                .toList();
        List<ToolUserPolicyItemResponse> tools = view.tools().stream()
                .map(tool -> new ToolUserPolicyItemResponse(
                        tool.toolId(),
                        tool.accessScope(),
                        tool.users().stream()
                        .map(rule -> new UserPolicyItemResponse(rule.userId(), rule.updatedAt()))
                        .toList()))
                .toList();
        return new UserPolicyResponse(
                view.agentId(),
                view.accessScope(),
                agentUsers,
                tools,
                view.updatedAt());
    }

    @PutMapping("/admin/agents/{agentId}/user-policies")
    UserPolicySaveResult replace(
            @PathVariable String agentId,
            @Valid @RequestBody SaveUserPolicyRequest request) {
        ensureNotBlank(agentId, "agentId must not be blank");
        List<UserAccessUpdate> agentUsers = expandUsers(request.agentUsers());
        List<ToolUserPolicyUpdate> tools = request.tools().stream()
                .map(tool -> new ToolUserPolicyUpdate(
                        tool.toolId(),
                        tool.accessScope(),
                        expandUsers(tool.users())))
                .toList();
        return service.replacePolicy(agentId, new UserPolicyUpdate(
                request.accessScope(),
                agentUsers,
                tools));
    }

    private List<UserAccessUpdate> expandUsers(List<UserPolicyItemRequest> users) {
        return UserIdBatchParser.parse(users.stream()
                        .map(user -> user == null ? null : user.userId())
                        .toList()).stream()
                .map(UserAccessUpdate::new)
                .toList();
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
