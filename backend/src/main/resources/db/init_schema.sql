-- Kariya Admin 全新数据库表结构（MySQL 8.0+）
-- 仅面向空数据库；已有数据库请使用专用迁移工具，不要重复执行本文件。

SET NAMES utf8mb4;

CREATE TABLE sys_dept
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id      BIGINT       NOT NULL DEFAULT 0,
    ancestors      VARCHAR(500) NOT NULL DEFAULT '0',
    dept_name      VARCHAR(80)  NOT NULL,
    dept_code      VARCHAR(80)  NOT NULL UNIQUE,
    leader_user_id BIGINT NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    status         TINYINT      NOT NULL DEFAULT 1,
    builtin        TINYINT      NOT NULL DEFAULT 0,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_by     BIGINT NULL,
    created_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT NULL,
    updated_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time   DATETIME NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_user
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    username             VARCHAR(64)  NOT NULL UNIQUE,
    employee_no          VARCHAR(64) NULL,
    employee_no_verified TINYINT      NOT NULL DEFAULT 0,
    real_name            VARCHAR(64)  NOT NULL,
    phone                VARCHAR(32) NULL,
    email                VARCHAR(128) NULL,
    dept_id              BIGINT NULL,
    registration_source  VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    status               TINYINT      NOT NULL DEFAULT 1,
    auth_version         BIGINT       NOT NULL DEFAULT 1,
    builtin              TINYINT      NOT NULL DEFAULT 0,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    created_by           BIGINT NULL,
    created_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT NULL,
    updated_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time         DATETIME NULL,
    UNIQUE KEY uk_user_employee_no (employee_no),
    INDEX idx_user_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_local_credential
(
    user_id                  BIGINT PRIMARY KEY,
    password_hash            VARCHAR(100) NOT NULL,
    password_change_required TINYINT      NOT NULL DEFAULT 0,
    enabled                  TINYINT      NOT NULL DEFAULT 1,
    password_changed_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_external_identity
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    provider_key          VARCHAR(64)  NOT NULL,
    issuer                VARCHAR(255) NOT NULL DEFAULT '',
    subject               VARCHAR(255) NOT NULL,
    display_name_snapshot VARCHAR(128) NULL,
    email_snapshot        VARCHAR(128) NULL,
    created_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_external_subject (provider_key, issuer, subject),
    UNIQUE KEY uk_user_provider (user_id, provider_key),
    INDEX idx_external_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_role
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name    VARCHAR(80) NOT NULL,
    role_code    VARCHAR(80) NOT NULL UNIQUE,
    data_scope   VARCHAR(32) NOT NULL DEFAULT 'SELF',
    status       TINYINT     NOT NULL DEFAULT 1,
    builtin      TINYINT     NOT NULL DEFAULT 0,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_by   BIGINT NULL,
    created_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT NULL,
    updated_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time DATETIME NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_menu
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id       BIGINT      NOT NULL DEFAULT 0,
    menu_name       VARCHAR(80) NOT NULL,
    menu_type       VARCHAR(10) NOT NULL,
    route_name      VARCHAR(80) NULL,
    route_path      VARCHAR(160) NULL,
    component       VARCHAR(160) NULL,
    permission_code VARCHAR(120) NULL,
    icon            VARCHAR(80) NULL,
    sort_order      INT         NOT NULL DEFAULT 0,
    visible         TINYINT     NOT NULL DEFAULT 1,
    status          TINYINT     NOT NULL DEFAULT 1,
    keep_alive      TINYINT     NOT NULL DEFAULT 0,
    builtin         TINYINT     NOT NULL DEFAULT 0,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    created_by      BIGINT NULL,
    created_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT NULL,
    updated_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time    DATETIME NULL,
    UNIQUE KEY uk_menu_route_path (route_path),
    UNIQUE KEY uk_menu_permission_code (permission_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_role_menu
(
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 部门变更审批主单：保存申请人、原/目标部门、当前阶段与最终状态。
CREATE TABLE sys_department_change_request
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id   BIGINT       NOT NULL,
    from_dept_id   BIGINT NULL,
    target_dept_id BIGINT       NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    current_step   INT          NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 1,
    finished_time  DATETIME NULL,
    created_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept_change_requester (requester_id, status),
    INDEX idx_dept_change_status (status, updat ed_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 部门变更审批步骤：每条主单的原部门/目标部门审批人与决策记录。
CREATE TABLE sys_department_change_step
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id       BIGINT       NOT NULL,
    step_order       INT          NOT NULL,
    step_type        VARCHAR(16)  NOT NULL,
    dept_id          BIGINT NULL,
    assigned_user_id BIGINT NULL,
    status           VARCHAR(16)  NOT NULL,
    decided_by       BIGINT NULL,
    decision_reason  VARCHAR(500) NULL,
    decided_time     DATETIME NULL,
    created_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dept_change_step (request_id, step_order),
    INDEX idx_dept_change_assignee (assigned_user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_notification
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id  BIGINT       NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    content       VARCHAR(550) NOT NULL,
    business_type VARCHAR(64) NULL,
    business_id   BIGINT NULL,
    read_time     DATETIME NULL,
    created_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_recipient (recipient_id, created_time),
    INDEX idx_notification_unread (recipient_id, read_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_login_log
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    user_id    BIGINT NULL,
    login_ip   VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    result     VARCHAR(16) NOT NULL,
    message    VARCHAR(255) NULL,
    login_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_log_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sys_operation_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NULL,
    username     VARCHAR(64) NULL,
    module       VARCHAR(80) NOT NULL,
    action       VARCHAR(80) NOT NULL,
    request_ip   VARCHAR(64) NULL,
    result       VARCHAR(16) NOT NULL,
    duration_ms  BIGINT NULL,
    created_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_log_time (created_time),
    INDEX idx_operation_log_module (module, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
