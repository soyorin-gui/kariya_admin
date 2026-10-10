CREATE TABLE intranet_transaction_message_field
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT       NOT NULL,
    message_side   VARCHAR(16)  NOT NULL,
    parent_id      BIGINT       NOT NULL DEFAULT 0,
    node_type      VARCHAR(16)  NOT NULL,
    field_name_cn  VARCHAR(64)  NOT NULL,
    field_name_en  VARCHAR(64)  NOT NULL,
    data_type      VARCHAR(64)  NULL,
    data_length    VARCHAR(64)  NULL,
    required_flag  TINYINT      NOT NULL DEFAULT 0,
    description    VARCHAR(1000) NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    created_by     BIGINT       NULL,
    created_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT       NULL,
    updated_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intranet_message_field_tree (transaction_id, message_side, parent_id, sort_order, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
