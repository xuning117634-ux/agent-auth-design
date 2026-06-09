CREATE TABLE IF NOT EXISTS agent_tool_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    auth_mode VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_policy_agent_tool (agent_id, tool_id),
    KEY idx_agent_tool_policy_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
