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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        Set<String> userIds = new LinkedHashSet<>();
        for (UserPolicyItemRequest user : users) {
            if (user == null || user.userId() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "userId must not be blank");
            }
            List<String> parsedUserIds = Arrays.stream(user.userId().split("[,;，；\\r\\n]+"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (parsedUserIds.isEmpty()) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "userId must contain at least one valid value");
            }
            userIds.addAll(parsedUserIds);
        }
        return userIds.stream()
                .map(UserAccessUpdate::new)
                .toList();
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
