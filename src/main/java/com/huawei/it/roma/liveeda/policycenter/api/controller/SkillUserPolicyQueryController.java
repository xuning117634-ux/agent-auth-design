package com.huawei.it.roma.liveeda.policycenter.api.controller;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleSkillItemResponse;
import com.huawei.it.roma.liveeda.policycenter.api.dto.AccessibleSkillListResponse;
import com.huawei.it.roma.liveeda.policycenter.service.SkillUserPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SkillUserPolicyQueryController {

    private final SkillUserPolicyService service;

    public SkillUserPolicyQueryController(SkillUserPolicyService service) {
        this.service = service;
    }

    @GetMapping("/internal/agents/{agentId}/users/{userId}/skills")
    AccessibleSkillListResponse listAccessibleSkills(
            @PathVariable String agentId,
            @PathVariable String userId) {
        ensureNotBlank(agentId, "agentId must not be blank");
        ensureNotBlank(userId, "userId must not be blank");
        List<AccessibleSkillItemResponse> skills = service.listAccessibleSkills(agentId, userId).stream()
                .map(skill -> new AccessibleSkillItemResponse(
                        skill.skillId(),
                        skill.skillName(),
                        skill.label(),
                        skill.description()))
                .toList();
        return new AccessibleSkillListResponse(agentId, userId, skills);
    }

    private void ensureNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
