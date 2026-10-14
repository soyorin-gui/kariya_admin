-- 内测库结构更新：执行前可清理内测需求数据。
-- 交易关联统一以 intranet_requirement.transaction_id 为准，删除明细中的重复字段。
ALTER TABLE intranet_requirement_transaction_change_detail DROP COLUMN transaction_id;
ALTER TABLE intranet_requirement_transaction_call_detail DROP COLUMN transaction_code;
ALTER TABLE intranet_requirement MODIFY COLUMN status TINYINT NOT NULL COMMENT '0草稿 1审批中 2开发中 3已拒绝 4已废弃 5已投产';

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

CREATE TABLE intranet_transaction_field_source_config (
    target_field_id BIGINT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL COMMENT 'PHYSICAL/CALLER_INPUT/CONSTANT/SYSTEM_GENERATED/COMPUTED',
    source_detail VARCHAR(2000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE intranet_field_lineage_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_field_id BIGINT NOT NULL,
    target_field_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    expression VARCHAR(2000) NULL,
    remark VARCHAR(1000) NULL,
    UNIQUE KEY uk_intranet_field_lineage (source_type, source_field_id, target_field_id, relation_type),
    INDEX idx_intranet_field_lineage_target (target_field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
