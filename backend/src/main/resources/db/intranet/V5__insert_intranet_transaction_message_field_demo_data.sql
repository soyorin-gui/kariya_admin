-- P1-02 虚构报文字段演示数据：先执行 V1、V3、V4；请勿用于生产真实数据。
-- 使用交易编码定位，不依赖自增主键值。

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT id, 'REQUEST', 0, 'FIELD', '客户号', 'customerNo', 'String', '32', 1, '客户唯一标识。', 10
FROM intranet_transaction WHERE transaction_code = 'DEMO_CUST_QUERY';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT id, 'RESPONSE', 0, 'OBJECT', '客户基本信息', 'customerProfile', NULL, NULL, 1, '客户基础资料对象。', 10
FROM intranet_transaction WHERE transaction_code = 'DEMO_CUST_QUERY';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT parent.transaction_id, 'RESPONSE', parent.id, 'FIELD', '客户名称', 'customerName', 'String', '128', 1, '客户展示名称。', 10
FROM intranet_transaction_message_field parent
JOIN intranet_transaction transaction_asset ON transaction_asset.id = parent.transaction_id
WHERE transaction_asset.transaction_code = 'DEMO_CUST_QUERY' AND parent.message_side = 'RESPONSE' AND parent.field_name_en = 'customerProfile';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT parent.transaction_id, 'RESPONSE', parent.id, 'FIELD', '客户等级', 'customerLevel', 'String', '16', 0, '客户等级代码。', 20
FROM intranet_transaction_message_field parent
JOIN intranet_transaction transaction_asset ON transaction_asset.id = parent.transaction_id
WHERE transaction_asset.transaction_code = 'DEMO_CUST_QUERY' AND parent.message_side = 'RESPONSE' AND parent.field_name_en = 'customerProfile';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT id, 'REQUEST', 0, 'FIELD', '开始日期', 'startDate', 'Date', 'yyyy-MM-dd', 1, '导出范围起始日期。', 10
FROM intranet_transaction WHERE transaction_code = 'DEMO_TXN_EXPORT';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT id, 'REQUEST', 0, 'FIELD', '结束日期', 'endDate', 'Date', 'yyyy-MM-dd', 1, '导出范围结束日期。', 20
FROM intranet_transaction WHERE transaction_code = 'DEMO_TXN_EXPORT';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT id, 'RESPONSE', 0, 'ARRAY', '交易流水列表', 'transactions', NULL, NULL, 1, '交易流水数组。', 10
FROM intranet_transaction WHERE transaction_code = 'DEMO_TXN_EXPORT';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT parent.transaction_id, 'RESPONSE', parent.id, 'OBJECT', '交易流水项', 'transactionItem', NULL, NULL, 1, '单笔交易流水对象。', 10
FROM intranet_transaction_message_field parent
JOIN intranet_transaction transaction_asset ON transaction_asset.id = parent.transaction_id
WHERE transaction_asset.transaction_code = 'DEMO_TXN_EXPORT' AND parent.message_side = 'RESPONSE' AND parent.field_name_en = 'transactions';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT parent.transaction_id, 'RESPONSE', parent.id, 'FIELD', '交易流水号', 'transactionId', 'String', '64', 1, '单笔交易的流水号。', 10
FROM intranet_transaction_message_field parent
JOIN intranet_transaction transaction_asset ON transaction_asset.id = parent.transaction_id
WHERE transaction_asset.transaction_code = 'DEMO_TXN_EXPORT' AND parent.message_side = 'RESPONSE' AND parent.field_name_en = 'transactionItem';

INSERT INTO intranet_transaction_message_field
    (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, data_length, required_flag, description, sort_order)
SELECT parent.transaction_id, 'RESPONSE', parent.id, 'FIELD', '交易金额', 'amount', 'Decimal', '18,2', 1, '交易金额。', 20
FROM intranet_transaction_message_field parent
JOIN intranet_transaction transaction_asset ON transaction_asset.id = parent.transaction_id
WHERE transaction_asset.transaction_code = 'DEMO_TXN_EXPORT' AND parent.message_side = 'RESPONSE' AND parent.field_name_en = 'transactionItem';
