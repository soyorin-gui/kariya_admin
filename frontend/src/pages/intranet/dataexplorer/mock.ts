import type { DataExplorerEnvironment, DataExplorerQuery, DataExplorerQueryResult, DataExplorerResource, DataSourceType, QueryTemplate } from './types';

const delay = (milliseconds: number) => new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));

const environments: DataExplorerEnvironment[] = [
  { id: 'sandbox', name: '沙箱环境', connectionLabel: 'Mock · sandbox' },
  { id: 'integration', name: '集成环境', connectionLabel: 'Mock · integration' },
];

const resources: DataExplorerResource[] = [
  { id: 'es-customer-profile', sourceType: 'ELASTICSEARCH', name: 'customer-profile-v1', description: '客户画像索引', detail: '12,840 documents' },
  { id: 'es-transaction', sourceType: 'ELASTICSEARCH', name: 'transaction-events-2026.10', description: '交易事件索引', detail: '86,201 documents' },
  { id: 'hbase-customer', sourceType: 'HBASE', group: 'customer', name: 'profile', description: '客户画像宽表', detail: 'cf:base, cf:risk' },
  { id: 'hbase-transaction', sourceType: 'HBASE', group: 'transaction', name: 'detail', description: '交易明细宽表', detail: 'cf:request, cf:response' },
  { id: 'redis-customer-10001', sourceType: 'REDIS', name: 'cache:customer:10001', description: 'String · TTL 1,724 s' },
  { id: 'redis-session-10001', sourceType: 'REDIS', name: 'session:customer:10001', description: 'Hash · TTL 3,581 s' },
  { id: 'redis-risk-queue', sourceType: 'REDIS', name: 'queue:risk:pending', description: 'List · 3 entries' },
  { id: 'redis-tags-10001', sourceType: 'REDIS', name: 'tags:customer:10001', description: 'Set · 3 members' },
];

const templates: QueryTemplate[] = [
  { id: 'es-match-all', sourceType: 'ELASTICSEARCH', name: '全部文档', description: '返回前 10 条文档', queryText: '{\n  "query": { "match_all": {} },\n  "size": 10\n}' },
  { id: 'es-customer-search', sourceType: 'ELASTICSEARCH', name: '客户名称检索', description: '按客户名称匹配', queryText: '{\n  "query": {\n    "match": { "customerName": "张" }\n  },\n  "sort": [{ "updatedAt": "desc" }]\n}' },
  { id: 'es-error', sourceType: 'ELASTICSEARCH', name: '错误演示', description: '模拟 ES 查询语法错误', queryText: '{\n  "query": { "invalid": true }\n}' },
  { id: 'hbase-get', sourceType: 'HBASE', name: 'RowKey Get', description: '按 RowKey 读取单行', queryText: '{\n  "operation": "GET",\n  "rowKey": "CUST#10001"\n}' },
  { id: 'hbase-scan', sourceType: 'HBASE', name: '范围 Scan', description: '按 RowKey 范围扫描', queryText: '{\n  "operation": "SCAN",\n  "startRow": "CUST#10001",\n  "stopRow": "CUST#10010",\n  "limit": 10\n}' },
  { id: 'redis-get', sourceType: 'REDIS', name: 'GET', description: '读取 String 类型 Key', queryText: 'GET cache:customer:10001' },
  { id: 'redis-hgetall', sourceType: 'REDIS', name: 'HGETALL', description: '读取 Hash 全部字段', queryText: 'HGETALL session:customer:10001' },
  { id: 'redis-lrange', sourceType: 'REDIS', name: 'LRANGE', description: '读取 List 区间', queryText: 'LRANGE queue:risk:pending 0 -1' },
  { id: 'redis-smembers', sourceType: 'REDIS', name: 'SMEMBERS', description: '读取 Set 全部成员', queryText: 'SMEMBERS tags:customer:10001' },
];

const buildResult = (sourceType: DataSourceType, records: DataExplorerQueryResult['records'], columns: DataExplorerQueryResult['columns'], elapsedTimeMs: number, warnings: string[] = []): DataExplorerQueryResult => ({
  sourceType,
  columns,
  records,
  total: records.length,
  returnedCount: records.length,
  elapsedTimeMs,
  rawResult: JSON.stringify(records.map((record) => record.raw), null, 2),
  warnings,
});

export const dataExplorerMock = {
  async environments() {
    await delay(180);
    return environments;
  },
  async resources(sourceType: DataSourceType, keyword?: string) {
    await delay(180);
    const normalizedKeyword = keyword?.trim().toLowerCase();
    return resources.filter((resource) => resource.sourceType === sourceType && (!normalizedKeyword || `${resource.group ?? ''} ${resource.name}`.toLowerCase().includes(normalizedKeyword)));
  },
  async templates(sourceType: DataSourceType) {
    await delay(100);
    return templates.filter((template) => template.sourceType === sourceType);
  },
  async execute(query: DataExplorerQuery): Promise<DataExplorerQueryResult> {
    await delay(700);
    const text = query.queryText.toLowerCase();
    if (text.includes('invalid') || text.includes('error')) throw new Error('模拟查询失败：查询语法不符合当前数据源要求');
    if (text.includes('empty') || query.resourceId === 'es-transaction') return buildResult(query.sourceType, [], [], 84, ['未查询到符合条件的数据']);
    if (query.sourceType === 'ELASTICSEARCH') {
      const rows = [
        { id: 'es-1', values: { customerId: '10001', customerName: '张晓', riskLevel: 'LOW', updatedAt: '2026-10-16 09:21:04' }, raw: { _index: 'customer-profile-v1', _id: '10001', _source: { customerId: '10001', customerName: '张晓', riskLevel: 'LOW', updatedAt: '2026-10-16T09:21:04' } } },
        { id: 'es-2', values: { customerId: '10002', customerName: '张晨', riskLevel: 'MEDIUM', updatedAt: '2026-10-16 08:43:18' }, raw: { _index: 'customer-profile-v1', _id: '10002', _source: { customerId: '10002', customerName: '张晨', riskLevel: 'MEDIUM', updatedAt: '2026-10-16T08:43:18' } } },
      ];
      return buildResult('ELASTICSEARCH', rows, [{ key: 'customerId', title: '客户号', width: 130 }, { key: 'customerName', title: '客户名称', width: 160 }, { key: 'riskLevel', title: '风险等级', width: 130 }, { key: 'updatedAt', title: '更新时间', width: 190 }], 132);
    }
    if (query.sourceType === 'HBASE') {
      const scan = text.includes('scan');
      const rows = scan
        ? [
            { id: 'hbase-1', values: { rowKey: 'CUST#10001', name: '张晓', level: 'A', updatedAt: '2026-10-16 09:21:04' }, raw: { rowKey: 'CUST#10001', cells: { 'base:name': '张晓', 'risk:level': 'A', 'base:updatedAt': '2026-10-16 09:21:04' } } },
            { id: 'hbase-2', values: { rowKey: 'CUST#10002', name: '张晨', level: 'B', updatedAt: '2026-10-16 08:43:18' }, raw: { rowKey: 'CUST#10002', cells: { 'base:name': '张晨', 'risk:level': 'B', 'base:updatedAt': '2026-10-16 08:43:18' } } },
          ]
        : [{ id: 'hbase-1', values: { rowKey: 'CUST#10001', name: '张晓', level: 'A', updatedAt: '2026-10-16 09:21:04' }, raw: { rowKey: 'CUST#10001', cells: { 'base:name': '张晓', 'risk:level': 'A', 'base:updatedAt': '2026-10-16 09:21:04' } } }];
      return buildResult('HBASE', rows, [{ key: 'rowKey', title: 'RowKey', width: 180 }, { key: 'name', title: '客户名称', width: 150 }, { key: 'level', title: '风险等级', width: 130 }, { key: 'updatedAt', title: '更新时间', width: 190 }], scan ? 218 : 76);
    }
    const command = text.split(/\s+/)[0]?.toUpperCase();
    const redisData: unknown = command === 'HGETALL'
      ? { customerId: '10001', sessionId: 'S-20261016-001', lastAccess: '2026-10-16 09:42:11' }
      : command === 'LRANGE'
        ? ['risk-check:10001', 'risk-check:10002', 'risk-check:10005']
        : command === 'SMEMBERS'
          ? ['VIP', 'MOBILE_BANKING', 'RISK_WATCH']
          : 'customer-profile-cache-v3';
    const row = { id: 'redis-1', values: { command, key: query.resourceId ? resources.find((resource) => resource.id === query.resourceId)?.name : 'cache:customer:10001', type: Array.isArray(redisData) ? 'List/Set' : typeof redisData === 'object' ? 'Hash' : 'String', value: typeof redisData === 'string' ? redisData : JSON.stringify(redisData) }, raw: { command, data: redisData } };
    return buildResult('REDIS', [row], [{ key: 'command', title: '命令', width: 110 }, { key: 'key', title: 'Key', width: 240 }, { key: 'type', title: '数据类型', width: 120 }, { key: 'value', title: '返回值' }], 18);
  },
};
