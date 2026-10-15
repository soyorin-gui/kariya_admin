-- operation 测试案例表（MySQL 8.0+，执行前请确认当前数据库）
-- 案例请求参数以 JSON 文本保存；不保存响应、断言、环境地址或 SOAP 公共报文头。
CREATE TABLE IF NOT EXISTS intranet_operation_test_case (
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

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'intranet_operation_test_case';
