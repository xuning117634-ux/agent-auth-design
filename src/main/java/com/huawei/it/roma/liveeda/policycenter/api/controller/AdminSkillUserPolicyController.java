package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.dto.SaveSkillUserPolicyRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.SkillUserPolicyItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.SkillUserPolicyResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.UserPolicyItemRequest;
import com.huawei.it.roma.liveeda.policycenter.api.dto.UserPolicyItemResponse;
import com.huawei.it.roma.liveeda.policycenter.service.SkillUserPolicySaveResult;
import com.huawei.it.roma.liveeda.policycenter.service.SkillUserPolicyService;
import com.huawei.it.roma.liveeda.policycenter.service.SkillUserPolicyUpdate;
import com.huawei.it.roma.liveeda.policycenter.service.UserAccessUpdate;
import com.huawei.it.roma.liveeda.policycenter.service.UserIdBatchParser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminSkillUserPolicyController {

    private final SkillUserPolicyService service;

    public AdminSkillUserPolicyController(SkillUserPolicyService service) {
        this.service = service;
    }

    @GetMapping("/admin/agents/{agentId}/skill-user-policies")
    SkillUserPolicyResponse list(@PathVariable String agentId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        var view = service.listPolicy(agentId);
        List<SkillUserPolicyItemResponse> skills = view.skills().stream()
                .map(skill -> new SkillUserPolicyItemResponse(
                        skill.skillId(),
                        skill.skillName(),
                        skill.label(),
                        skill.description(),
                        skill.accessScope(),
                        skill.users().stream()
                                .map(rule -> new UserPolicyItemResponse(rule.userId(), rule.updatedAt()))
                                .toList()))
                .toList();
        return new SkillUserPolicyResponse(view.agentId(), skills, view.updatedAt());
    }

    @PutMapping("/admin/agents/{agentId}/skill-user-policies")
    SkillUserPolicySaveResult replace(
            @PathVariable String agentId,
            @Valid @RequestBody SaveSkillUserPolicyRequest request) {
        ensureNotBlank(agentId, "agentId must not be blank");
        List<SkillUserPolicyUpdate> skills = request.skills().stream()
                .map(skill -> new SkillUserPolicyUpdate(
                        skill.skillId(),
                        skill.accessScope(),
                        expandUsers(skill.users())))
                .toList();
        return service.replacePolicy(agentId, skills);
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
