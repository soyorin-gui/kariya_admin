    -- P1-01 虚构演示数据：仅在已执行 V1 后按需执行，请勿用于生产真实数据。
    INSERT INTO intranet_transaction
        (transaction_name, transaction_code, description, status, esf_service_name, esf_service_operation_id,
         esf_service_address, esf_service_operation_name, label, business_contact, data_timeliness,
         print_file_mode, sort_rule, data_validation_scope, query_scope, file_generation_scope, file_name_rule)
    VALUES
        ('客户信息查询', 'DEMO_CUST_QUERY', '演示：按客户号查询客户基础信息。', 2, 'customer-profile-service', 'queryCustomerProfile',
         'S12345678', '查询客户信息', '客户', '张三', '实时', 0, '按客户号升序', '客户号不能为空且长度不超过 32 位', '单次查询一个客户', NULL, NULL),
        ('交易流水导出', 'DEMO_TXN_EXPORT', '演示：按日期范围导出交易流水文件。', 1, 'transaction-export-service', 'exportTransactions',
         'S23456789', '导出交易流水', '导出', '李四', 'T+1', 1, '按交易时间倒序', '起止日期不能为空，跨度不超过 31 天', '近 31 个自然日', '指定日期范围内的交易流水', 'uuid.finish'),
        ('风险名单核验', 'DEMO_RISK_CHECK', '演示：同步核验客户风险名单状态。', 0, 'risk-screening-service', 'verifyRiskList',
         'S34567890', '核验风险名单', '风控', '王五', '实时', 2, '按命中等级降序', '证件号码不能为空', '单个客户实时核验', '当前核验结果', 'risk_{uuid}.finish');
