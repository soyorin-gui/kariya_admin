CREATE TABLE intranet_transaction_version
(
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id             BIGINT        NOT NULL,
    requirement_id             BIGINT        NOT NULL,
    approval_instance_id       BIGINT        NOT NULL,
    version_no                 INT           NOT NULL,
    active_flag                TINYINT       NOT NULL DEFAULT 1,
    transaction_name           VARCHAR(128)  NOT NULL,
    transaction_code           VARCHAR(64)   NOT NULL,
    description                VARCHAR(1000) NULL,
    transaction_status         TINYINT       NOT NULL,
    esf_service_name           VARCHAR(64)   NOT NULL,
    esf_service_operation_id   VARCHAR(64)   NOT NULL,
    esf_service_address        VARCHAR(64)   NOT NULL,
    esf_service_operation_name VARCHAR(64)   NOT NULL,
    label                      VARCHAR(10)   NULL,
    business_contact           VARCHAR(32)   NULL,
    data_timeliness            VARCHAR(50)   NULL,
    print_file_mode            TINYINT       NOT NULL DEFAULT 0,
    sort_rule                  VARCHAR(500)  NULL,
    data_validation_scope      VARCHAR(500)  NULL,
    query_scope                VARCHAR(100)  NULL,
    file_generation_scope      VARCHAR(500)  NULL,
    file_name_rule             VARCHAR(255)  NULL,
    activated_by               BIGINT        NULL,
    activated_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_time               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_version_no (transaction_id, version_no),
    UNIQUE KEY uk_intranet_transaction_version_requirement (requirement_id),
    INDEX idx_intranet_transaction_version_active (transaction_id, active_flag),
    INDEX idx_intranet_transaction_version_approval (approval_instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_transaction_version_message_field
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id               BIGINT        NOT NULL,
    source_field_id          BIGINT        NOT NULL,
    message_side             VARCHAR(16)   NOT NULL,
    parent_source_field_id   BIGINT        NOT NULL DEFAULT 0,
    node_type                VARCHAR(16)   NOT NULL,
    field_name_cn            VARCHAR(64)   NOT NULL,
    field_name_en            VARCHAR(64)   NOT NULL,
    data_type                VARCHAR(64)   NULL,
    data_length              VARCHAR(64)   NULL,
    required_flag            TINYINT       NOT NULL DEFAULT 0,
    description              VARCHAR(1000) NULL,
    sort_order               INT           NOT NULL DEFAULT 0,
    INDEX idx_intranet_version_message_field (version_id, message_side, parent_source_field_id, sort_order, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_transaction_version_hbase_resource
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id     BIGINT NOT NULL,
    hbase_table_id BIGINT NOT NULL,
    UNIQUE KEY uk_intranet_version_hbase_resource (version_id, hbase_table_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_transaction_version_es_resource
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id  BIGINT NOT NULL,
    es_index_id BIGINT NOT NULL,
    UNIQUE KEY uk_intranet_version_es_resource (version_id, es_index_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
