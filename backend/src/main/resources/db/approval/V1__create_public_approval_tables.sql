CREATE TABLE sys_approval_instance
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type    VARCHAR(64)   NOT NULL,
    business_id      BIGINT        NOT NULL,
    business_title   VARCHAR(255)  NOT NULL,
    business_summary VARCHAR(1000) NULL,
    requester_id     BIGINT        NOT NULL,
    requester_name   VARCHAR(64)   NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    current_step     INT           NOT NULL DEFAULT 1,
    approval_round   INT           NOT NULL DEFAULT 1,
    submitted_time   DATETIME      NOT NULL,
    finished_time    DATETIME      NULL,
    created_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_business (business_type, business_id),
    INDEX idx_approval_requester (requester_id, status, created_time),
    INDEX idx_approval_status (status, updated_time),
    INDEX idx_approval_business_type (business_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_approval_task
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_instance_id BIGINT       NOT NULL,
    approval_round       INT          NOT NULL,
    step_order           INT          NOT NULL,
    step_type            VARCHAR(32)  NOT NULL,
    step_name            VARCHAR(64)  NOT NULL,
    assigned_user_id     BIGINT       NULL,
    assigned_user_name   VARCHAR(64)  NULL,
    status               VARCHAR(16)  NOT NULL,
    decided_by           BIGINT       NULL,
    decided_by_name      VARCHAR(64)  NULL,
    decision_reason      VARCHAR(500) NULL,
    decided_time         DATETIME     NULL,
    created_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_task_step (approval_instance_id, approval_round, step_order),
    INDEX idx_approval_task_assignee (assigned_user_id, status, created_time),
    INDEX idx_approval_task_instance (approval_instance_id, approval_round)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
