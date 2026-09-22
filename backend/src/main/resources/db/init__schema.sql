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
);
CREATE TABLE sys_user
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    real_name     VARCHAR(64)  NOT NULL,
    phone         VARCHAR(32)  NOT NULL,
    email         VARCHAR(128) NULL,
    dept_id       BIGINT       NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 1,
    auth_version  BIGINT       NOT NULL DEFAULT 1,
    builtin       TINYINT      NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_by    BIGINT NULL,
    created_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT NULL,
    updated_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time  DATETIME NULL,
    INDEX         idx_user_dept(dept_id)
);
CREATE TABLE sys_role
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name    VARCHAR(80) NOT NULL,
    role_code    VARCHAR(80) NOT NULL UNIQUE,
    data_scope   VARCHAR(32) NOT NULL DEFAULT 'SELF',
    status       TINYINT     NOT NULL DEFAULT 1,
    builtin      TINYINT     NOT NULL DEFAULT 0,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE sys_menu
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id       BIGINT      NOT NULL DEFAULT 0,
    menu_name       VARCHAR(80) NOT NULL,
    menu_type       VARCHAR(10) NOT NULL,
    route_name      VARCHAR(80),
    route_path      VARCHAR(160),
    component       VARCHAR(160),
    permission_code VARCHAR(120),
    icon            VARCHAR(80),
    sort_order      INT         NOT NULL DEFAULT 0,
    visible         TINYINT     NOT NULL DEFAULT 1,
    status          TINYINT     NOT NULL DEFAULT 1,
    keep_alive      TINYINT     NOT NULL DEFAULT 0,
    builtin         TINYINT     NOT NULL DEFAULT 0,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    created_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE sys_user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE TABLE sys_role_menu
(
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);
CREATE TABLE sys_role_dept
(
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);
CREATE TABLE sys_login_log
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    user_id    BIGINT NULL,
    login_ip   VARCHAR(64),
    user_agent VARCHAR(500),
    result     VARCHAR(16) NOT NULL,
    message    VARCHAR(255),
    login_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE sys_operation_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NULL,
    username     VARCHAR(64),
    module       VARCHAR(80) NOT NULL,
    action       VARCHAR(80) NOT NULL,
    request_ip   VARCHAR(64),
    result       VARCHAR(16) NOT NULL,
    duration_ms  BIGINT,
    created_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
