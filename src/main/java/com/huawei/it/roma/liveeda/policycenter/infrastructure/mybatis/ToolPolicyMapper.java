package com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ToolPolicyMapper {

    ToolPolicyRecord selectByAgentIdAndToolId(
            @Param("agentId") String agentId,
            @Param("toolId") String toolId);

    List<ToolPolicyRecord> selectByAgentId(@Param("agentId") String agentId);

    void upsert(ToolPolicyRecord record);

    void deleteByAgentId(@Param("agentId") String agentId);

    void deleteMissing(
            @Param("agentId") String agentId,
            @Param("toolIds") List<String> toolIds);
}
