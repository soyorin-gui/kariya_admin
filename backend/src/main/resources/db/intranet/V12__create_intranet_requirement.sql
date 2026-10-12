CREATE TABLE intranet_requirement
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    oa_no            VARCHAR(50)   NOT NULL,
    requirement_type TINYINT       NOT NULL COMMENT '1新增交易 2交易变更 3交易调用',
    title            VARCHAR(255)  NOT NULL,
    transaction_id   BIGINT        NULL,
    status           TINYINT       NOT NULL COMMENT '0草稿 1审批中 2已通过 3已拒绝 4已撤销',
    developer_user_id BIGINT       NULL,
    developer_name   VARCHAR(64)   NULL,
    created_by       BIGINT        NULL,
    created_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT        NULL,
    updated_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intranet_requirement_oa (oa_no, status, updated_time),
    INDEX idx_intranet_requirement_transaction (transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_requirement_new_transaction_detail
(
    requirement_id BIGINT PRIMARY KEY,
    cost_business_contact VARCHAR(64) NULL, business_department VARCHAR(128) NULL,
    oa_contact_no VARCHAR(50) NULL, interface_data_fields TEXT NULL,
    data_source_system VARCHAR(255) NULL, calling_system VARCHAR(255) NULL,
    calling_system_owner VARCHAR(64) NULL, expected_online_date DATE NULL,
    user_query_behavior VARCHAR(32) NULL, data_projected_flag TINYINT NULL,
    third_category_detail TEXT NULL, five_category_detail TEXT NULL, architecture_detail TEXT NULL,
    service_window VARCHAR(16) NULL, response_time_requirement VARCHAR(64) NULL,
    concurrent_user_count INT NULL, tps_peak VARCHAR(64) NULL, tps_estimation_basis TEXT NULL,
    history_query_range VARCHAR(64) NULL, single_query_range VARCHAR(64) NULL,
    page_size INT NULL, daily_transaction_volume VARCHAR(64) NULL,
    generate_file_flag TINYINT NULL, file_generation_scope VARCHAR(1000) NULL,
    calculation_flag TINYINT NULL, calculation_scenario VARCHAR(2000) NULL,
    remarks VARCHAR(2000) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_requirement_transaction_call_detail
(
    requirement_id BIGINT PRIMARY KEY,
    cost_business_contact VARCHAR(64) NULL, business_department VARCHAR(128) NULL,
    oa_contact_no VARCHAR(50) NULL, interface_data_fields TEXT NULL,
    calling_system VARCHAR(255) NULL, calling_system_owner VARCHAR(64) NULL,
    expected_online_date DATE NULL, user_query_behavior VARCHAR(32) NULL,
    transaction_code VARCHAR(128) NULL, current_capability_met_flag TINYINT NULL,
    third_category_detail TEXT NULL, five_category_detail TEXT NULL, architecture_detail TEXT NULL,
    remarks VARCHAR(2000) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_requirement_transaction_change_detail
(
    requirement_id BIGINT PRIMARY KEY,
    transaction_id BIGINT NULL, esf_change TEXT NULL, data_logic_change TEXT NULL,
    transaction_logic_change TEXT NULL, remarks VARCHAR(2000) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
