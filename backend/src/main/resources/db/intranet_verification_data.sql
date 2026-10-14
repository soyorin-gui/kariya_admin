-- 内网模块全流程验证数据（MySQL 8.0+，仅限本地/内测库）
-- 警告：会删除全部 intranet_* 业务数据，以及 business_type=INTRANET_REQUIREMENT 的审批/通知。
-- 不删除 sys_user、sys_role、sys_menu、sys_dept 等基线数据；执行前请自行备份。
-- 前提：已执行 init_schema.sql、intranet_schema.sql，以及已有库所需的需求升级脚本。

SET NAMES utf8mb4;
SET @test_user_id = (SELECT id FROM sys_user ORDER BY id LIMIT 1);

-- 一、清理内网业务和仅属于内网需求的公共审批/通知记录。
DELETE FROM sys_notification WHERE business_type = 'INTRANET_REQUIREMENT';
DELETE t FROM sys_approval_task t JOIN sys_approval_instance i ON i.id = t.approval_instance_id WHERE i.business_type = 'INTRANET_REQUIREMENT';
DELETE FROM sys_approval_instance WHERE business_type = 'INTRANET_REQUIREMENT';
DELETE FROM intranet_transaction_version_message_field;
DELETE FROM intranet_transaction_version_hbase_resource;
DELETE FROM intranet_transaction_version_es_resource;
DELETE FROM intranet_transaction_version;
DELETE FROM intranet_requirement_flow;
DELETE FROM intranet_requirement_new_transaction_detail;
DELETE FROM intranet_requirement_transaction_call_detail;
DELETE FROM intranet_requirement_transaction_change_detail;
DELETE FROM intranet_requirement;
DELETE FROM intranet_field_lineage_relation;
DELETE FROM intranet_transaction_field_source_config;
DELETE FROM intranet_transaction_hbase_resource;
DELETE FROM intranet_transaction_es_resource;
DELETE FROM intranet_transaction_message_field;
DELETE FROM intranet_es_field;
DELETE FROM intranet_es_index;
DELETE FROM intranet_hbase_field;
DELETE FROM intranet_hbase_table;
DELETE FROM intranet_transaction;

-- 二、HBase / ES 元数据：包含“ES 检索后 RowKey 回表”和“直接 HBase 查询”两条路径。
INSERT INTO intranet_hbase_table (market_name, table_name, description, rowkey_rule, lock_key, data_volume, data_depth, created_by, updated_by)
VALUES ('RISK', 'CUSTOMER_PROFILE', '客户风险画像权威数据', 'customer_no', 'risk:customer_profile:lock', '1200万', '5年', @test_user_id, @test_user_id);
SET @hbase_customer = LAST_INSERT_ID();
INSERT INTO intranet_hbase_table (market_name, table_name, description, rowkey_rule, data_volume, data_depth, created_by, updated_by)
VALUES ('PAYMENT', 'TRANSACTION_DETAIL', '交易明细权威数据', 'transaction_id', '3亿', '3年', @test_user_id, @test_user_id);
SET @hbase_transaction = LAST_INSERT_ID();

INSERT INTO intranet_hbase_field (hbase_table_id, column_family, field_name, data_type, description, sort_order, created_by, updated_by) VALUES
(@hbase_customer, 'info', 'customer_no', 'varchar(32)', '客户号', 1, @test_user_id, @test_user_id),
(@hbase_customer, 'info', 'customer_name', 'varchar(128)', '客户名称', 2, @test_user_id, @test_user_id),
(@hbase_customer, 'risk', 'risk_level', 'varchar(16)', '风险等级', 3, @test_user_id, @test_user_id),
(@hbase_transaction, 'info', 'transaction_id', 'varchar(32)', '交易流水号', 1, @test_user_id, @test_user_id),
(@hbase_transaction, 'info', 'amount', 'decimal(18,2)', '交易金额', 2, @test_user_id, @test_user_id),
(@hbase_transaction, 'info', 'transaction_time', 'datetime', '交易时间', 3, @test_user_id, @test_user_id);
SET @hbase_customer_no = (SELECT id FROM intranet_hbase_field WHERE hbase_table_id=@hbase_customer AND field_name='customer_no');
SET @hbase_customer_name = (SELECT id FROM intranet_hbase_field WHERE hbase_table_id=@hbase_customer AND field_name='customer_name');
SET @hbase_risk_level = (SELECT id FROM intranet_hbase_field WHERE hbase_table_id=@hbase_customer AND field_name='risk_level');
SET @hbase_transaction_id = (SELECT id FROM intranet_hbase_field WHERE hbase_table_id=@hbase_transaction AND field_name='transaction_id');
SET @hbase_amount = (SELECT id FROM intranet_hbase_field WHERE hbase_table_id=@hbase_transaction AND field_name='amount');

INSERT INTO intranet_es_index (index_name, index_pattern, partition_rule, hbase_table_id, description, created_by, updated_by)
VALUES ('customer-profile', 'customer-profile-{yyyy}', '按年', @hbase_customer, '客户画像检索索引，id 保存 HBase RowKey', @test_user_id, @test_user_id);
SET @es_customer = LAST_INSERT_ID();
INSERT INTO intranet_es_field (es_index_id, field_name, data_type, searchable_flag, sortable_flag, description, sort_order, created_by, updated_by) VALUES
(@es_customer, 'id', 'keyword', 1, 0, '完整 HBase RowKey', 1, @test_user_id, @test_user_id),
(@es_customer, 'customer_name', 'text', 1, 0, '客户名称检索条件', 2, @test_user_id, @test_user_id),
(@es_customer, 'risk_level', 'keyword', 1, 1, '风险等级检索条件', 3, @test_user_id, @test_user_id);
SET @es_customer_name = (SELECT id FROM intranet_es_field WHERE es_index_id=@es_customer AND field_name='customer_name');

-- 三、两笔开发中交易及请求/响应字段。
INSERT INTO intranet_transaction (transaction_name, transaction_code, description, status, esf_service_name, esf_service_operation_id, esf_service_address, esf_service_operation_name, label, business_contact, print_file_mode, created_by, updated_by)
VALUES ('客户风险画像查询', 'DEMO_RISK_PROFILE_QUERY', '通过 ES 检索并回表查询客户风险画像', 1, 'risk-profile-service', 'queryProfile', '/risk/profile/query', '查询客户风险画像', '风险', '测试用户', 0, @test_user_id, @test_user_id);
SET @transaction_risk = LAST_INSERT_ID();
INSERT INTO intranet_transaction (transaction_name, transaction_code, description, status, esf_service_name, esf_service_operation_id, esf_service_address, esf_service_operation_name, label, business_contact, print_file_mode, created_by, updated_by)
VALUES ('交易明细查询', 'DEMO_TRANSACTION_DETAIL_QUERY', '根据交易流水号直接查询 HBase', 1, 'transaction-detail-service', 'queryDetail', '/transaction/detail/query', '查询交易明细', '交易', '测试用户', 0, @test_user_id, @test_user_id);
SET @transaction_detail = LAST_INSERT_ID();

INSERT INTO intranet_transaction_message_field (transaction_id, message_side, parent_id, node_type, field_name_cn, field_name_en, data_type, required_flag, description, sort_order, created_by, updated_by) VALUES
(@transaction_risk, 'REQUEST', 0, 'FIELD', '客户名称', 'customerName', 'String', 1, 'ES 检索条件', 1, @test_user_id, @test_user_id),
(@transaction_risk, 'RESPONSE', 0, 'FIELD', '客户名称', 'customerName', 'String', 1, 'HBase 返回字段', 1, @test_user_id, @test_user_id),
(@transaction_risk, 'RESPONSE', 0, 'FIELD', '风险等级', 'riskLevel', 'String', 1, 'HBase 返回字段', 2, @test_user_id, @test_user_id),
(@transaction_detail, 'REQUEST', 0, 'FIELD', '交易流水号', 'transactionId', 'String', 1, '直接查询条件', 1, @test_user_id, @test_user_id),
(@transaction_detail, 'REQUEST', 0, 'FIELD', '页码', 'page', 'Integer', 0, '调用方分页参数', 2, @test_user_id, @test_user_id),
(@transaction_detail, 'RESPONSE', 0, 'FIELD', '交易金额', 'amount', 'Decimal', 1, 'HBase 返回字段', 1, @test_user_id, @test_user_id);
SET @risk_request_name = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_risk AND message_side='REQUEST' AND field_name_en='customerName');
SET @risk_response_name = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_risk AND message_side='RESPONSE' AND field_name_en='customerName');
SET @risk_response_level = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_risk AND message_side='RESPONSE' AND field_name_en='riskLevel');
SET @detail_request_id = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_detail AND message_side='REQUEST' AND field_name_en='transactionId');
SET @detail_request_page = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_detail AND message_side='REQUEST' AND field_name_en='page');
SET @detail_response_amount = (SELECT id FROM intranet_transaction_message_field WHERE transaction_id=@transaction_detail AND message_side='RESPONSE' AND field_name_en='amount');

INSERT INTO intranet_transaction_hbase_resource (transaction_id, hbase_table_id, created_by, updated_by) VALUES (@transaction_risk, @hbase_customer, @test_user_id, @test_user_id), (@transaction_detail, @hbase_transaction, @test_user_id, @test_user_id);
INSERT INTO intranet_transaction_es_resource (transaction_id, es_index_id, created_by, updated_by) VALUES (@transaction_risk, @es_customer, @test_user_id, @test_user_id);

-- 四、字段来源与血缘：ES 查询条件、HBase 直接读取、调用方分页参数。
INSERT INTO intranet_transaction_field_source_config (target_field_id, source_type, source_detail) VALUES
(@risk_request_name, 'PHYSICAL', '客户名称由 ES 检索条件匹配'), (@risk_response_name, 'PHYSICAL', 'ES 命中后按 RowKey 回表读取'), (@risk_response_level, 'PHYSICAL', 'ES 命中后按 RowKey 回表读取'),
(@detail_request_id, 'PHYSICAL', '交易流水号直接定位 HBase RowKey'), (@detail_request_page, 'CALLER_INPUT', '调用方传入分页参数'), (@detail_response_amount, 'PHYSICAL', '直接读取交易明细金额');
INSERT INTO intranet_field_lineage_relation (source_type, source_field_id, target_field_id, relation_type, expression, remark) VALUES
('ES_FIELD', @es_customer_name, @risk_request_name, 'QUERY_CONDITION', 'customer_name match', 'ES 查询条件'),
('HBASE_FIELD', @hbase_customer_name, @risk_response_name, 'DIRECT', NULL, 'ES 命中后 RowKey 回表'),
('HBASE_FIELD', @hbase_risk_level, @risk_response_level, 'DIRECT', NULL, 'ES 命中后 RowKey 回表'),
('HBASE_FIELD', @hbase_transaction_id, @detail_request_id, 'QUERY_CONDITION', 'transaction_id = request.transactionId', '直接 HBase 查询条件'),
('HBASE_FIELD', @hbase_amount, @detail_response_amount, 'DIRECT', NULL, '直接 HBase 返回字段');

-- 五、三类草稿需求：用于从新增、变更、调用入口开始完整手工验证审批与实施。
INSERT INTO intranet_requirement (oa_no, requirement_type, title, description, expected_online_date, status, created_by, updated_by) VALUES ('OA-VERIFY-NEW-001', 1, '验证：新增客户标签交易', '用于验证新增交易需求全流程', DATE_ADD(CURDATE(), INTERVAL 30 DAY), 0, @test_user_id, @test_user_id);
SET @requirement_new = LAST_INSERT_ID();
INSERT INTO intranet_requirement_new_transaction_detail (requirement_id, cost_business_contact, business_department, interface_data_fields, data_source_system, calling_system, calling_system_owner, expected_online_date, user_query_behavior, data_projected_flag, remarks) VALUES (@requirement_new, '测试联系人', '风险管理部', 'customerNo, customerTag', '客户主数据', '风险门户', '测试负责人', DATE_ADD(CURDATE(), INTERVAL 30 DAY), '查询', 0, '验证数据');
INSERT INTO intranet_requirement (oa_no, requirement_type, title, description, transaction_id, expected_online_date, status, created_by, updated_by) VALUES ('OA-VERIFY-CHANGE-001', 2, '验证：客户风险画像字段变更', '用于验证交易变更需求全流程', @transaction_risk, DATE_ADD(CURDATE(), INTERVAL 20 DAY), 0, @test_user_id, @test_user_id);
SET @requirement_change = LAST_INSERT_ID();
INSERT INTO intranet_requirement_transaction_change_detail (requirement_id, esf_change, data_logic_change, transaction_logic_change, remarks) VALUES (@requirement_change, '增加风险等级返回字段', '读取 risk_level', '响应增加 riskLevel', '验证数据');
INSERT INTO intranet_requirement (oa_no, requirement_type, title, description, transaction_id, expected_online_date, status, created_by, updated_by) VALUES ('OA-VERIFY-CALL-001', 3, '验证：交易调用接入', '用于验证交易调用需求全流程', @transaction_detail, DATE_ADD(CURDATE(), INTERVAL 15 DAY), 0, @test_user_id, @test_user_id);
SET @requirement_call = LAST_INSERT_ID();
INSERT INTO intranet_requirement_transaction_call_detail (requirement_id, cost_business_contact, business_department, interface_data_fields, calling_system, calling_system_owner, expected_online_date, user_query_behavior, current_capability_met_flag, remarks) VALUES (@requirement_call, '测试联系人', '渠道运营部', 'transactionId, amount', '验证调用方', '测试负责人', DATE_ADD(CURDATE(), INTERVAL 15 DAY), '查询', 1, '验证数据');
INSERT INTO intranet_requirement_flow (requirement_id, event_type, event_name, remark, operator_id, operator_name) VALUES (@requirement_new, 'CREATE', '创建草稿', '验证数据初始化', @test_user_id, '测试用户'), (@requirement_change, 'CREATE', '创建草稿', '验证数据初始化', @test_user_id, '测试用户'), (@requirement_call, 'CREATE', '创建草稿', '验证数据初始化', @test_user_id, '测试用户');

SELECT '验证数据初始化完成' AS result, @transaction_risk AS risk_transaction_id, @transaction_detail AS detail_transaction_id, @requirement_new AS new_requirement_id, @requirement_change AS change_requirement_id, @requirement_call AS call_requirement_id;
