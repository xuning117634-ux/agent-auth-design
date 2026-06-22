CREATE DATABASE IF NOT EXISTS policy_center
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE policy_center;

-- Local compatibility mirror of the externally managed Skill directory table.
CREATE TABLE IF NOT EXISTS agent_policy_skill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id VARCHAR(128) NOT NULL,
    skill_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NULL,
    created_by VARCHAR(50) NOT NULL DEFAULT 'system',
    created_date DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_updated_by VARCHAR(50) NOT NULL DEFAULT 'system',
    last_updated_date DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    label VARCHAR(255) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_skill_policy_agent_skill (agent_id, skill_id),
    KEY idx_agent_policy_skill_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
