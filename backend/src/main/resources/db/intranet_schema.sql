-- 内网模块最终结构基线（MySQL 8.0+）
-- 用于全新部署：先执行 ../init_schema.sql，再执行本文件。
-- 本文件仅创建结构，不插入菜单、角色、用户、权限或演示数据；这些数据由目标内网环境自行初始化。
-- 已有数据库不得重复执行，应按实际表结构进行核对。

SET NAMES utf8mb4;

CREATE TABLE intranet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_name VARCHAR(128) NOT NULL,
    transaction_code VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    status TINYINT NOT NULL DEFAULT 0,
    esf_service_name VARCHAR(64) NOT NULL,
    esf_service_operation_id VARCHAR(64) NOT NULL,
    esf_service_address VARCHAR(64) NOT NULL,
    esf_service_operation_name VARCHAR(64) NOT NULL,
    label VARCHAR(10) NULL,
    business_contact VARCHAR(32) NULL,
    data_timeliness VARCHAR(50) NULL,
    print_file_mode TINYINT NOT NULL DEFAULT 0,
    sort_rule VARCHAR(500) NULL,
    data_validation_scope VARCHAR(500) NULL,
    query_scope VARCHAR(100) NULL,
    file_generation_scope VARCHAR(500) NULL,
    file_name_rule VARCHAR(255) NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_code (transaction_code),
    INDEX idx_intranet_transaction_name (transaction_name),
    INDEX idx_intranet_transaction_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_message_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    message_side VARCHAR(16) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    node_type VARCHAR(16) NOT NULL,
    field_name_cn VARCHAR(64) NOT NULL,
    field_name_en VARCHAR(64) NOT NULL,
    data_type VARCHAR(64) NULL,
    data_length VARCHAR(64) NULL,
    required_flag TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_message_field_sibling_name (transaction_id, message_side, parent_id, field_name_en),
    INDEX idx_intranet_message_field_tree (transaction_id, message_side, parent_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_field_source_config (
    target_field_id BIGINT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL COMMENT 'PHYSICAL/CALLER_INPUT/CONSTANT/SYSTEM_GENERATED/COMPUTED',
    source_detail VARCHAR(2000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_field_lineage_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL COMMENT 'HBASE_FIELD/ES_FIELD; future types supported',
    source_field_id BIGINT NOT NULL,
    target_field_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL COMMENT 'DIRECT/DERIVED/QUERY_CONDITION',
    expression VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    UNIQUE KEY uk_intranet_field_lineage (source_type, source_field_id, target_field_id, relation_type),
    INDEX idx_intranet_field_lineage_target (target_field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_hbase_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    market_name VARCHAR(64) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    rowkey_rule VARCHAR(1000) NULL,
    lock_key VARCHAR(255) NULL,
    data_volume VARCHAR(64) NULL,
    data_depth VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_hbase_table_market_name (market_name, table_name),
    INDEX idx_intranet_hbase_table_updated_time (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_hbase_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hbase_table_id BIGINT NOT NULL,
    column_family VARCHAR(64) NOT NULL DEFAULT 'info',
    field_name VARCHAR(128) NOT NULL,
    data_type VARCHAR(128) NULL,
    description VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_hbase_field (hbase_table_id, column_family, field_name),
    INDEX idx_intranet_hbase_field_order (hbase_table_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_es_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    index_name VARCHAR(128) NOT NULL,
    index_pattern VARCHAR(255) NOT NULL,
    partition_rule VARCHAR(64) NULL,
    hbase_table_id BIGINT NOT NULL,
    description VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_es_index_pattern (index_pattern),
    INDEX idx_intranet_es_index_hbase_table (hbase_table_id),
    INDEX idx_intranet_es_index_updated_time (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_es_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    es_index_id BIGINT NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    searchable_flag TINYINT NOT NULL DEFAULT 0,
    sortable_flag TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_es_field (es_index_id, field_name),
    INDEX idx_intranet_es_field_order (es_index_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_hbase_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    hbase_table_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_hbase_resource (transaction_id, hbase_table_id),
    INDEX idx_intranet_transaction_hbase_table (hbase_table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_es_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    es_index_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_es_resource (transaction_id, es_index_id),
    INDEX idx_intranet_transaction_es_index (es_index_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_requirement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    oa_no VARCHAR(50) NOT NULL,
    requirement_type TINYINT NOT NULL COMMENT '1新增交易 2交易变更 3交易调用',
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    transaction_id BIGINT NULL,
    expected_online_date DATE NULL,
    status TINYINT NOT NULL COMMENT '0草稿 1审批中 2开发中 3已拒绝 4已废弃 5已投产',
    developer_user_id BIGINT NULL,
    developer_name VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intranet_requirement_oa (oa_no, status, updated_time),
    INDEX idx_intranet_requirement_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_requirement_new_transaction_detail (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_requirement_transaction_call_detail (
    requirement_id BIGINT PRIMARY KEY,
    cost_business_contact VARCHAR(64) NULL, business_department VARCHAR(128) NULL,
    oa_contact_no VARCHAR(50) NULL, interface_data_fields TEXT NULL,
    calling_system VARCHAR(255) NULL, calling_system_owner VARCHAR(64) NULL,
    expected_online_date DATE NULL, user_query_behavior VARCHAR(32) NULL,
    current_capability_met_flag TINYINT NULL,
    third_category_detail TEXT NULL, five_category_detail TEXT NULL, architecture_detail TEXT NULL,
    remarks VARCHAR(2000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_requirement_transaction_change_detail (
    requirement_id BIGINT PRIMARY KEY,
    esf_change TEXT NULL,
    data_logic_change TEXT NULL,
    transaction_logic_change TEXT NULL,
    remarks VARCHAR(2000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_requirement_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    remark VARCHAR(1000) NULL,
    operator_id BIGINT NULL,
    operator_name VARCHAR(64) NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_intranet_requirement_flow (requirement_id, created_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    requirement_id BIGINT NOT NULL,
    approval_instance_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    active_flag TINYINT NOT NULL DEFAULT 1,
    transaction_name VARCHAR(128) NOT NULL,
    transaction_code VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    transaction_status TINYINT NOT NULL,
    esf_service_name VARCHAR(64) NOT NULL,
    esf_service_operation_id VARCHAR(64) NOT NULL,
    esf_service_address VARCHAR(64) NOT NULL,
    esf_service_operation_name VARCHAR(64) NOT NULL,
    label VARCHAR(10) NULL,
    business_contact VARCHAR(32) NULL,
    data_timeliness VARCHAR(50) NULL,
    print_file_mode TINYINT NOT NULL DEFAULT 0,
    sort_rule VARCHAR(500) NULL,
    data_validation_scope VARCHAR(500) NULL,
    query_scope VARCHAR(100) NULL,
    file_generation_scope VARCHAR(500) NULL,
    file_name_rule VARCHAR(255) NULL,
    activated_by BIGINT NULL,
    activated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_version_no (transaction_id, version_no),
    UNIQUE KEY uk_intranet_transaction_version_requirement (requirement_id),
    INDEX idx_intranet_transaction_version_active (transaction_id, active_flag),
    INDEX idx_intranet_transaction_version_approval (approval_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_version_message_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    source_field_id BIGINT NOT NULL,
    message_side VARCHAR(16) NOT NULL,
    parent_source_field_id BIGINT NOT NULL DEFAULT 0,
    node_type VARCHAR(16) NOT NULL,
    field_name_cn VARCHAR(64) NOT NULL,
    field_name_en VARCHAR(64) NOT NULL,
    data_type VARCHAR(64) NULL,
    data_length VARCHAR(64) NULL,
    required_flag TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    INDEX idx_intranet_version_message_field (version_id, message_side, parent_source_field_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_version_hbase_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    hbase_table_id BIGINT NOT NULL,
    UNIQUE KEY uk_intranet_version_hbase_resource (version_id, hbase_table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_transaction_version_es_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    es_index_id BIGINT NOT NULL,
    UNIQUE KEY uk_intranet_version_es_resource (version_id, es_index_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_operation_test_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    binding_type VARCHAR(16) NOT NULL,
    transaction_version_id BIGINT NULL,
    case_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    request_payload LONGTEXT NOT NULL,
    created_by BIGINT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_operation_case_binding (transaction_id, binding_type, transaction_version_id, case_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
