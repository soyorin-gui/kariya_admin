-- P1-03 虚构数据资产演示数据：先执行 V8；交易关联演示还依赖 V3。
-- 仅用于本地/测试环境查看页面效果，请勿用于生产真实数据。
-- 本脚本按业务唯一键做幂等保护，可重复执行。

INSERT INTO intranet_hbase_table
    (market_name, table_name, description, rowkey_rule, lock_key, data_volume, data_depth)
SELECT 'HDS', 'CUSTOMER_PROFILE', '虚构客户画像宽表，用于演示直接查询 HBase 与 ES 回表两种访问方式。',
       'customerNo(32) + reverse(eventDate yyyyMMdd)', 'demo:lock:hds:customer-profile', '约 1.2 亿行', '保留近 5 年'
WHERE NOT EXISTS (
    SELECT 1 FROM intranet_hbase_table WHERE market_name = 'HDS' AND table_name = 'CUSTOMER_PROFILE'
);

INSERT INTO intranet_hbase_table
    (market_name, table_name, description, rowkey_rule, lock_key, data_volume, data_depth)
SELECT 'HDS', 'TRANSACTION_DETAIL', '虚构交易流水明细表，演示按月拆分 ES 索引后使用 id 字段回查全量数据。',
       'accountNo(24) + transactionDate(yyyyMMdd) + sequenceNo(12)', NULL, '约 8.6 亿行', '保留近 7 年'
WHERE NOT EXISTS (
    SELECT 1 FROM intranet_hbase_table WHERE market_name = 'HDS' AND table_name = 'TRANSACTION_DETAIL'
);

INSERT INTO intranet_hbase_table
    (market_name, table_name, description, rowkey_rule, lock_key, data_volume, data_depth)
SELECT 'PBM', 'RISK_HIT_RESULT', '虚构风险规则命中结果表。锁标识为空，用于验证可选配置的展示效果。',
       'idNoHash(64) + hitDate(yyyyMMdd) + ruleCode(16)', NULL, '约 3200 万行', '保留近 3 年'
WHERE NOT EXISTS (
    SELECT 1 FROM intranet_hbase_table WHERE market_name = 'PBM' AND table_name = 'RISK_HIT_RESULT'
);

INSERT INTO intranet_hbase_field (hbase_table_id, column_family, field_name, data_type, description, sort_order)
SELECT t.id, 'info', seed.field_name, seed.data_type, seed.description, seed.sort_order
FROM intranet_hbase_table t
CROSS JOIN (
    SELECT 'customer_no' field_name, 'varchar(32)' data_type, '虚构客户唯一标识。' description, 10 sort_order
    UNION ALL SELECT 'customer_name', 'varchar(128)', '客户展示名称。', 20
    UNION ALL SELECT 'customer_level', 'varchar(16)', '客户等级代码。', 30
    UNION ALL SELECT 'updated_at', 'datetime', '画像最近更新时间。', 40
) seed
WHERE t.market_name = 'HDS' AND t.table_name = 'CUSTOMER_PROFILE'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_hbase_field f
      WHERE f.hbase_table_id = t.id AND f.column_family = 'info' AND f.field_name = seed.field_name
  );

INSERT INTO intranet_hbase_field (hbase_table_id, column_family, field_name, data_type, description, sort_order)
SELECT t.id, 'info', seed.field_name, seed.data_type, seed.description, seed.sort_order
FROM intranet_hbase_table t
CROSS JOIN (
    SELECT 'transaction_id' field_name, 'varchar(64)' data_type, '虚构交易流水号。' description, 10 sort_order
    UNION ALL SELECT 'account_no', 'varchar(24)', '虚构账号。', 20
    UNION ALL SELECT 'transaction_time', 'datetime', '交易发生时间。', 30
    UNION ALL SELECT 'amount', 'decimal(18,2)', '交易金额。', 40
    UNION ALL SELECT 'currency', 'varchar(3)', '币种代码。', 50
) seed
WHERE t.market_name = 'HDS' AND t.table_name = 'TRANSACTION_DETAIL'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_hbase_field f
      WHERE f.hbase_table_id = t.id AND f.column_family = 'info' AND f.field_name = seed.field_name
  );

INSERT INTO intranet_hbase_field (hbase_table_id, column_family, field_name, data_type, description, sort_order)
SELECT t.id, 'info', seed.field_name, seed.data_type, seed.description, seed.sort_order
FROM intranet_hbase_table t
CROSS JOIN (
    SELECT 'subject_hash' field_name, 'varchar(64)' data_type, '虚构主体标识散列值。' description, 10 sort_order
    UNION ALL SELECT 'rule_code', 'varchar(16)', '风险规则编码。', 20
    UNION ALL SELECT 'hit_level', 'int', '命中等级。', 30
    UNION ALL SELECT 'hit_time', 'datetime', '规则命中时间。', 40
) seed
WHERE t.market_name = 'PBM' AND t.table_name = 'RISK_HIT_RESULT'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_hbase_field f
      WHERE f.hbase_table_id = t.id AND f.column_family = 'info' AND f.field_name = seed.field_name
  );

INSERT INTO intranet_es_index (index_name, index_pattern, partition_rule, hbase_table_id, description)
SELECT '客户画像检索', 'hds_customer_profile_yyyy', '按年', t.id, '物理索引示例：hds_customer_profile_2026。'
FROM intranet_hbase_table t
WHERE t.market_name = 'HDS' AND t.table_name = 'CUSTOMER_PROFILE'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_index WHERE index_pattern = 'hds_customer_profile_yyyy');

INSERT INTO intranet_es_index (index_name, index_pattern, partition_rule, hbase_table_id, description)
SELECT '交易流水检索', 'hds_transaction_detail_yyyyMM', '按月', t.id, '物理索引示例：hds_transaction_detail_202610。'
FROM intranet_hbase_table t
WHERE t.market_name = 'HDS' AND t.table_name = 'TRANSACTION_DETAIL'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_index WHERE index_pattern = 'hds_transaction_detail_yyyyMM');

INSERT INTO intranet_es_index (index_name, index_pattern, partition_rule, hbase_table_id, description)
SELECT '风险命中检索', 'pbm_risk_hit_result_yyyy', '按年', t.id, '风险规则命中结果的逻辑索引族。'
FROM intranet_hbase_table t
WHERE t.market_name = 'PBM' AND t.table_name = 'RISK_HIT_RESULT'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_index WHERE index_pattern = 'pbm_risk_hit_result_yyyy');

INSERT INTO intranet_es_field (es_index_id, field_name, data_type, searchable_flag, sortable_flag, description, sort_order)
SELECT i.id, seed.field_name, seed.data_type, seed.searchable_flag, seed.sortable_flag, seed.description, seed.sort_order
FROM intranet_es_index i
CROSS JOIN (
    SELECT 'id' field_name, 'keyword' data_type, 1 searchable_flag, 0 sortable_flag, '完整 HBase RowKey，固定字段。' description, 10 sort_order
    UNION ALL SELECT 'customer_no', 'keyword', 1, 0, '虚构客户唯一标识。', 20
    UNION ALL SELECT 'customer_name', 'text', 1, 0, '客户名称全文检索字段。', 30
    UNION ALL SELECT 'updated_at', 'date', 1, 1, '画像更新时间。', 40
) seed
WHERE i.index_pattern = 'hds_customer_profile_yyyy'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_field f WHERE f.es_index_id = i.id AND f.field_name = seed.field_name);

INSERT INTO intranet_es_field (es_index_id, field_name, data_type, searchable_flag, sortable_flag, description, sort_order)
SELECT i.id, seed.field_name, seed.data_type, seed.searchable_flag, seed.sortable_flag, seed.description, seed.sort_order
FROM intranet_es_index i
CROSS JOIN (
    SELECT 'id' field_name, 'keyword' data_type, 1 searchable_flag, 0 sortable_flag, '完整 HBase RowKey，固定字段。' description, 10 sort_order
    UNION ALL SELECT 'account_no', 'keyword', 1, 0, '虚构账号。', 20
    UNION ALL SELECT 'transaction_time', 'date', 1, 1, '交易发生时间。', 30
    UNION ALL SELECT 'amount', 'numeric', 1, 1, '交易金额。', 40
    UNION ALL SELECT 'summary', 'text', 1, 0, '交易摘要全文检索字段。', 50
) seed
WHERE i.index_pattern = 'hds_transaction_detail_yyyyMM'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_field f WHERE f.es_index_id = i.id AND f.field_name = seed.field_name);

INSERT INTO intranet_es_field (es_index_id, field_name, data_type, searchable_flag, sortable_flag, description, sort_order)
SELECT i.id, seed.field_name, seed.data_type, seed.searchable_flag, seed.sortable_flag, seed.description, seed.sort_order
FROM intranet_es_index i
CROSS JOIN (
    SELECT 'id' field_name, 'keyword' data_type, 1 searchable_flag, 0 sortable_flag, '完整 HBase RowKey，固定字段。' description, 10 sort_order
    UNION ALL SELECT 'subject_hash', 'keyword', 1, 0, '虚构主体标识散列值。', 20
    UNION ALL SELECT 'rule_code', 'keyword', 1, 1, '风险规则编码。', 30
    UNION ALL SELECT 'hit_level', 'numeric', 1, 1, '命中等级。', 40
    UNION ALL SELECT 'hit_time', 'date', 1, 1, '命中时间。', 50
) seed
WHERE i.index_pattern = 'pbm_risk_hit_result_yyyy'
  AND NOT EXISTS (SELECT 1 FROM intranet_es_field f WHERE f.es_index_id = i.id AND f.field_name = seed.field_name);

-- 可选的交易资源关联演示：目标演示交易不存在时不会插入任何记录。
INSERT INTO intranet_transaction_hbase_resource (transaction_id, hbase_table_id)
SELECT tx.id, hb.id
FROM intranet_transaction tx
JOIN intranet_hbase_table hb ON hb.market_name = 'HDS' AND hb.table_name = 'CUSTOMER_PROFILE'
WHERE tx.transaction_code = 'DEMO_CUST_QUERY'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_transaction_hbase_resource r
      WHERE r.transaction_id = tx.id AND r.hbase_table_id = hb.id
  );

INSERT INTO intranet_transaction_es_resource (transaction_id, es_index_id)
SELECT tx.id, es.id
FROM intranet_transaction tx
JOIN intranet_es_index es ON es.index_pattern = 'hds_transaction_detail_yyyyMM'
WHERE tx.transaction_code = 'DEMO_TXN_EXPORT'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_transaction_es_resource r
      WHERE r.transaction_id = tx.id AND r.es_index_id = es.id
  );

INSERT INTO intranet_transaction_es_resource (transaction_id, es_index_id)
SELECT tx.id, es.id
FROM intranet_transaction tx
JOIN intranet_es_index es ON es.index_pattern = 'pbm_risk_hit_result_yyyy'
WHERE tx.transaction_code = 'DEMO_RISK_CHECK'
  AND NOT EXISTS (
      SELECT 1 FROM intranet_transaction_es_resource r
      WHERE r.transaction_id = tx.id AND r.es_index_id = es.id
  );
