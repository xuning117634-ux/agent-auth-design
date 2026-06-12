CREATE DATABASE IF NOT EXISTS policy_center
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE policy_center;

-- Incremental schema for docs/02-policy-center/07-user-policy-design.md.
-- Existing environments that already have agent_tool_policy only need this file.

CREATE TABLE IF NOT EXISTS agent_user_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    access_scope VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_user_policy_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_tool_user_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    access_scope VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_user_policy_agent_tool (agent_id, tool_id),
    KEY idx_agent_tool_user_policy_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_user_access_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_user_access_policy_agent_user (agent_id, user_id),
    KEY idx_agent_user_access_policy_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_tool_user_access_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_user_access_policy_agent_tool_user (agent_id, tool_id, user_id),
    KEY idx_agent_tool_user_access_policy_user_id (user_id),
    KEY idx_agent_tool_user_access_policy_agent_tool (agent_id, tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
