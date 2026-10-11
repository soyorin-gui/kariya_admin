CREATE TABLE intranet_hbase_table
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    market_name  VARCHAR(64)   NOT NULL,
    table_name   VARCHAR(128)  NOT NULL,
    description  VARCHAR(1000) NULL,
    rowkey_rule  VARCHAR(1000) NULL,
    lock_key     VARCHAR(255)  NULL,
    data_volume  VARCHAR(64)   NULL,
    data_depth   VARCHAR(64)   NULL,
    created_by   BIGINT        NULL,
    created_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT        NULL,
    updated_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_hbase_table_market_name (market_name, table_name),
    INDEX idx_intranet_hbase_table_updated_time (updated_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_hbase_field
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    hbase_table_id BIGINT        NOT NULL,
    column_family  VARCHAR(64)   NOT NULL DEFAULT 'info',
    field_name     VARCHAR(128)  NOT NULL,
    data_type      VARCHAR(128)  NULL,
    description    VARCHAR(1000) NULL,
    sort_order     INT           NOT NULL DEFAULT 0,
    created_by     BIGINT        NULL,
    created_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT        NULL,
    updated_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_hbase_field (hbase_table_id, column_family, field_name),
    INDEX idx_intranet_hbase_field_order (hbase_table_id, sort_order, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_es_index
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    index_name     VARCHAR(128)  NOT NULL,
    index_pattern  VARCHAR(255)  NOT NULL,
    partition_rule VARCHAR(64)   NULL,
    hbase_table_id BIGINT        NOT NULL,
    description    VARCHAR(1000) NULL,
    created_by     BIGINT        NULL,
    created_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT        NULL,
    updated_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_es_index_pattern (index_pattern),
    INDEX idx_intranet_es_index_hbase_table (hbase_table_id),
    INDEX idx_intranet_es_index_updated_time (updated_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_es_field
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    es_index_id     BIGINT        NOT NULL,
    field_name      VARCHAR(128)  NOT NULL,
    data_type       VARCHAR(64)   NOT NULL,
    searchable_flag TINYINT       NOT NULL DEFAULT 0,
    sortable_flag   TINYINT       NOT NULL DEFAULT 0,
    description     VARCHAR(1000) NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    created_by      BIGINT        NULL,
    created_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT        NULL,
    updated_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_es_field (es_index_id, field_name),
    INDEX idx_intranet_es_field_order (es_index_id, sort_order, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_transaction_hbase_resource
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT   NOT NULL,
    hbase_table_id BIGINT   NOT NULL,
    created_by     BIGINT   NULL,
    created_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT   NULL,
    updated_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_hbase_resource (transaction_id, hbase_table_id),
    INDEX idx_intranet_transaction_hbase_table (hbase_table_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE intranet_transaction_es_resource
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT   NOT NULL,
    es_index_id    BIGINT   NOT NULL,
    created_by     BIGINT   NULL,
    created_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT   NULL,
    updated_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_intranet_transaction_es_resource (transaction_id, es_index_id),
    INDEX idx_intranet_transaction_es_index (es_index_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
