-- 执行前检查是否已有重复数据；若查询有结果，应先由业务确认如何处理，禁止脚本自动删除。
SELECT transaction_id, message_side, parent_id, field_name_en, COUNT(*) AS duplicate_count
FROM intranet_transaction_message_field
GROUP BY transaction_id, message_side, parent_id, field_name_en
HAVING COUNT(*) > 1;

-- 同一交易、同一报文方向、同一父节点下，字段英文名唯一。
-- 若上方查询存在重复记录，本语句会失败并保留原数据。
ALTER TABLE intranet_transaction_message_field
    ADD UNIQUE KEY uk_intranet_message_field_sibling_name
        (transaction_id, message_side, parent_id, field_name_en);
