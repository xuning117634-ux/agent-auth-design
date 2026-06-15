package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

public interface AgentPolicyToolMapper {

    AgentPolicyToolRecord selectBoundTool(
            @Param("agentId") String agentId,
            @Param("serviceId") String serviceId,
            @Param("toolName") String toolName);
}
