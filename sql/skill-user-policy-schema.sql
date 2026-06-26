CREATE TABLE IF NOT EXISTS agent_skill_user_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    skill_id VARCHAR(255) NOT NULL,
    access_scope VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_skill_user_policy_agent_skill (agent_id, skill_id),
    KEY idx_agent_skill_user_policy_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_skill_user_access_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    skill_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_skill_user_access_policy_agent_skill_user (agent_id, skill_id, user_id),
    KEY idx_agent_skill_user_access_policy_user_id (user_id),
    KEY idx_agent_skill_user_access_policy_agent_skill (agent_id, skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
