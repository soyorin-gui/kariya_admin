import { useMemo, useRef, useState, type Key } from 'react';
import { App, Avatar, Badge, Button, Card, Col, DatePicker, Divider, Input, Progress, Row, Segmented, Select, Space, Switch, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckCircleOutlined, CodeOutlined, CopyOutlined, DeploymentUnitOutlined, DownloadOutlined, LineChartOutlined, PlayCircleOutlined, ReloadOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import type { GraphOptions } from '@antv/g6';
import type { Dayjs } from 'dayjs';
import { BaseChart } from '../../components/BaseChart';
import { CodeViewer, type CodeLanguage } from '../../components/CodeViewer';
import { CopyButton } from '../../components/CopyButton';
import { G6Graph } from '../../components/G6Graph';
import { SmartTable, type SmartTableRef } from '../../components/SmartTable';
import { StatusTag } from '../../components/StatusTag';
import { useThemePreference } from '../../theme/ThemeProvider';
import type { TableRequestParams } from '../../types/table';
import { downloadBlob } from '../../utils/download';
import './home.css';

interface CodeSample {
  key: string;
  label: string;
  language: CodeLanguage;
  filename: string;
  value: string;
}

const CODE_SAMPLES: CodeSample[] = [
  {
    key: 'typescript',
    label: 'TypeScript',
    language: 'typescript',
    filename: 'smart-table-demo.tsx',
    value: `import { SmartTable } from '@/components/SmartTable';

type UserSearch = { keyword: string; status?: number };

<SmartTable<User, UserSearch>
  rowKey="id"
  columns={columns}
  initialSearch={{ keyword: '' }}
  request={async ({ page, pageSize, sorter, search }) => {
    const data = await getUsers({
      pageNum: page,
      pageSize,
      keyword: search.keyword,
      sortField: sorter?.field,
      sortOrder: sorter?.order,
    });
    return { list: data.records, total: data.total };
  }}
/>;`,
  },
  {
    key: 'xml',
    label: 'XML',
    language: 'xml',
    filename: 'application-context.xml',
    value: `<application name="kariya-admin">
  <datasource driver="com.mysql.cj.jdbc.Driver">
    <pool min-size="5" max-size="20" />
    <validation timeout="3000" enabled="true" />
  </datasource>
  <features>
    <feature name="audit-log" enabled="true" />
    <feature name="file-import" enabled="false" />
  </features>
</application>`,
  },
  {
    key: 'json',
    label: 'JSON',
    language: 'json',
    filename: 'user-page.json',
    value: `{"code":0,"message":"success","data":{"records":[{"id":1001,"username":"lin.chen","status":"ACTIVE"},{"id":1002,"username":"mei.zhou","status":"PENDING"}],"page":1,"pageSize":10,"total":42}}`,
  },
  {
    key: 'sql',
    label: 'SQL',
    language: 'sql',
    filename: 'active-users.sql',
    value: `SELECT
  u.id,
  u.username,
  d.dept_name,
  COUNT(ur.role_id) AS role_count
FROM sys_user AS u
LEFT JOIN sys_dept AS d ON d.id = u.dept_id
LEFT JOIN sys_user_role AS ur ON ur.user_id = u.id
WHERE u.status = 1
  AND u.deleted = 0
GROUP BY u.id, u.username, d.dept_name
ORDER BY u.created_time DESC;`,
  },
  {
    key: 'java',
    label: 'Java',
    language: 'java',
    filename: 'UserController.java',
    value: `@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UserView>> page(UserQuery query) {
        return ApiResponse.ok(userService.page(query));
    }
}`,
  },
  {
    key: 'yaml',
    label: 'YAML',
    language: 'yaml',
    filename: 'application.yml',
    value: `server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/kariya_admin
    hikari:
      maximum-pool-size: 20
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics`,
  },
];

type DemoStatus = 'ACTIVE' | 'PENDING' | 'DISABLED';
type DemoPriority = 'HIGH' | 'MEDIUM' | 'LOW';

interface DemoTableRow {
  id: number;
  name: string;
  department: string;
  status: DemoStatus;
  priority: DemoPriority;
  score: number;
  updatedAt: string;
}

type DemoTableSearch = {
  keyword: string;
  department?: string;
  status?: DemoStatus;
  range: [Dayjs, Dayjs] | null;
};

const DEPARTMENTS = ['研发中心', '产品设计', '市场运营', '客户成功'];
const DEMO_NAMES = ['陈晓宇', '林若溪', '周子墨', '沈知夏', '苏景行', '顾清欢', '陆时安', '叶星澜'];
const DEMO_STATUSES: DemoStatus[] = ['ACTIVE', 'PENDING', 'DISABLED'];
const DEMO_PRIORITIES: DemoPriority[] = ['HIGH', 'MEDIUM', 'LOW'];
const STATUS_META: Record<DemoStatus, { label: string; status: 'success' | 'warning' | 'default' }> = {
  ACTIVE: { label: '启用', status: 'success' },
  PENDING: { label: '待审核', status: 'warning' },
  DISABLED: { label: '停用', status: 'default' },
};
const PRIORITY_META: Record<DemoPriority, { label: string; color: string }> = {
  HIGH: { label: '高', color: 'red' },
  MEDIUM: { label: '中', color: 'gold' },
  LOW: { label: '低', color: 'blue' },
};
const MOCK_TABLE_ROWS: DemoTableRow[] = Array.from({ length: 47 }, (_, index) => ({
  id: 1001 + index,
  name: DEMO_NAMES[index % DEMO_NAMES.length],
  department: DEPARTMENTS[index % DEPARTMENTS.length],
  status: DEMO_STATUSES[index % DEMO_STATUSES.length],
  priority: DEMO_PRIORITIES[(index * 2) % DEMO_PRIORITIES.length],
  score: 62 + ((index * 7) % 37),
  updatedAt: `2026-09-${String(2 + (index % 26)).padStart(2, '0')} ${String(8 + (index % 10)).padStart(2, '0')}:${String((index * 7) % 60).padStart(2, '0')}:00`,
}));

function applyTableQuery(params: TableRequestParams<DemoTableSearch>): DemoTableRow[] {
  const keyword = params.search.keyword.trim().toLowerCase();
  let rows = MOCK_TABLE_ROWS.filter((row) => {
    const searchMatched = !keyword || row.name.toLowerCase().includes(keyword) || String(row.id).includes(keyword);
    const departmentMatched = !params.search.department || row.department === params.search.department;
    const statusMatched = !params.search.status || row.status === params.search.status;
    const date = row.updatedAt.slice(0, 10);
    const rangeMatched = !params.search.range || (date >= params.search.range[0].format('YYYY-MM-DD') && date <= params.search.range[1].format('YYYY-MM-DD'));
    const statusFilters = Array.isArray(params.filters?.status) ? params.filters.status : [];
    const priorityFilters = Array.isArray(params.filters?.priority) ? params.filters.priority : [];
    return searchMatched && departmentMatched && statusMatched && rangeMatched
      && (!statusFilters.length || statusFilters.includes(row.status))
      && (!priorityFilters.length || priorityFilters.includes(row.priority));
  });
  if (params.sorter?.field && params.sorter.order) {
    const field = params.sorter.field as keyof DemoTableRow;
    const direction = params.sorter.order === 'asc' ? 1 : -1;
    rows = [...rows].sort((left, right) => String(left[field]).localeCompare(String(right[field]), 'zh-CN', { numeric: true }) * direction);
  }
  return rows;
}

function csvCell(value: unknown): string {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

const BASE_CHART_SNIPPET = `import { BaseChart } from '@/components/BaseChart';

const option = {
  xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed'] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: [18, 32, 26] }],
};

<BaseChart option={option} height={280} />;`;

function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '');
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** 组件展示页不读取业务接口；所有内容均为本地 mock，供后续页面开发参考。 */
export default function HomePage() {
  const { message } = App.useApp();
  const { primaryColor, semantic } = useThemePreference();
  const tableRef = useRef<SmartTableRef<DemoTableSearch>>(null);
  const [dense, setDense] = useState(false);
  const [segment, setSegment] = useState<string | number>('概览');
  const [draft, setDraft] = useState('可在此输入内容，观察 Ant Design Input 的默认状态。');
  const [activeCode, setActiveCode] = useState(CODE_SAMPLES[0].key);
  const [codeDrafts, setCodeDrafts] = useState<Record<string, string>>(() => Object.fromEntries(CODE_SAMPLES.map((sample) => [sample.key, sample.value])));
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);

  const currentCode = CODE_SAMPLES.find((sample) => sample.key === activeCode) ?? CODE_SAMPLES[0];

  const tableColumns = useMemo<ColumnsType<DemoTableRow>>(
    () => [
      { title: '编号', dataIndex: 'id', width: 100, sorter: true },
      { title: '姓名', dataIndex: 'name', width: 120, sorter: true },
      { title: '部门', dataIndex: 'department', width: 130 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        filters: Object.entries(STATUS_META).map(([value, meta]) => ({ value, text: meta.label })),
        render: (value: DemoStatus) => <StatusTag status={STATUS_META[value].status}>{STATUS_META[value].label}</StatusTag>,
      },
      {
        title: '优先级',
        dataIndex: 'priority',
        width: 105,
        filters: Object.entries(PRIORITY_META).map(([value, meta]) => ({ value, text: meta.label })),
        render: (value: DemoPriority) => <Tag color={PRIORITY_META[value].color}>{PRIORITY_META[value].label}</Tag>,
      },
      { title: '评分', dataIndex: 'score', width: 100, sorter: true, render: (value: number) => <Progress percent={value} size='small' showInfo={false} /> },
      { title: '最近更新', dataIndex: 'updatedAt', width: 180, sorter: true },
      {
        title: '操作',
        key: 'actions',
        width: 120,
        fixed: 'right',
        render: (_, row) => <Button type='link' size='small' onClick={() => message.info(`查看 Mock 记录：${row.name}（${row.id}）`)}>查看</Button>,
      },
    ],
    [message],
  );

  const exportTable = (params: TableRequestParams<DemoTableSearch>) => {
    const rows = applyTableQuery(params);
    const lines = [
      ['编号', '姓名', '部门', '状态', '优先级', '评分', '最近更新'].map(csvCell).join(','),
      ...rows.map((row) => [row.id, row.name, row.department, STATUS_META[row.status].label, PRIORITY_META[row.priority].label, row.score, row.updatedAt].map(csvCell).join(',')),
    ];
    downloadBlob(new Blob([`\uFEFF${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' }), 'smart-table-demo.csv');
    message.success(`已导出当前条件下的 ${rows.length} 条 Mock 数据`);
  };

  const chartOption = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 20, top: 34, bottom: 30 },
      xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], axisLine: { lineStyle: { color: '#d7e1ef' } } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
      series: [
        {
          name: 'Mock requests',
          type: 'line',
          smooth: true,
          data: [24, 36, 28, 52, 46, 68, 59],
          symbolSize: 7,
          lineStyle: { width: 3, color: primaryColor },
          itemStyle: { color: primaryColor },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: withAlpha(primaryColor, 0.3) },
              { offset: 1, color: withAlpha(primaryColor, 0.02) },
            ]),
          },
        },
      ],
    }),
    [primaryColor],
  );

  const graphOptions = useMemo<Omit<GraphOptions, 'container' | 'height' | 'autoResize'>>(
    () => ({
      data: {
        nodes: [
          { id: 'client', style: { x: 80, y: 130, size: 48, fill: withAlpha(primaryColor, 0.14), stroke: primaryColor, lineWidth: 2, labelText: 'Client', labelPlacement: 'bottom' } },
          { id: 'api', style: { x: 245, y: 72, size: 52, fill: primaryColor, stroke: primaryColor, labelText: 'API', labelFill: primaryColor, labelPlacement: 'bottom' } },
          { id: 'cache', style: { x: 245, y: 194, size: 48, fill: withAlpha(semantic.success, 0.16), stroke: semantic.success, lineWidth: 2, labelText: 'Cache', labelPlacement: 'bottom' } },
          { id: 'database', style: { x: 420, y: 130, size: 52, fill: withAlpha(semantic.warning, 0.16), stroke: semantic.warning, lineWidth: 2, labelText: 'Database', labelPlacement: 'bottom' } },
        ],
        edges: [
          { source: 'client', target: 'api', style: { stroke: withAlpha(primaryColor, 0.58), lineWidth: 2, endArrow: true } },
          { source: 'api', target: 'cache', style: { stroke: withAlpha(semantic.success, 0.62), lineWidth: 2, endArrow: true } },
          { source: 'api', target: 'database', style: { stroke: withAlpha(semantic.warning, 0.62), lineWidth: 2, endArrow: true } },
        ],
      },
      node: { type: 'circle' },
      edge: { type: 'line' },
      // G6 v5 已将 v4 的 drag-node 改名为 drag-element。
      behaviors: ['drag-canvas', 'drag-element', 'zoom-canvas'],
    }),
    [primaryColor, semantic.success, semantic.warning],
  );

  return (
    <div className='component-lab-page'>
      <div className='page-head component-lab-head'>
        <div>
          <div className='page-kicker'>COMPONENT LAB</div>
          <Typography.Title level={2} className='page-title'>
            前端组件示例
          </Typography.Title>
          <Typography.Paragraph type='secondary' className='component-lab-subtitle'>
            所有内容均为本地 mock，用作后续页面开发时的样式和调用参考。
          </Typography.Paragraph>
        </div>
        <Tag color='processing' icon={<CodeOutlined />}>
          开发参考页
        </Tag>
      </div>

      <Card className='showcase-card antd-showcase' title='Ant Design 基础交互' extra={<Badge status='success' text='Mock state' />}>
        <Row gutter={[24, 20]}>
          <Col xs={24} lg={13}>
            <div className='demo-label'>按钮、状态与反馈</div>
            <Space wrap size={[10, 10]}>
              <Button type='primary' icon={<PlayCircleOutlined />} onClick={() => message.success('这是一个成功反馈示例')}>
                主按钮
              </Button>
              <Button icon={<CopyOutlined />} onClick={() => message.info('这是一个普通反馈示例')}>
                普通按钮
              </Button>
              <Button danger>危险操作</Button>
              <StatusTag status='success'>运行正常</StatusTag>
              <StatusTag status='warning'>等待处理</StatusTag>
              <StatusTag status='error'>请求失败</StatusTag>
            </Space>
            <Divider />
            <div className='demo-label'>输入与选择</div>
            <Space direction='vertical' size={10} className='demo-input-stack'>
              <Input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder='输入任意内容' />
              <Space wrap>
                <Segmented value={segment} onChange={setSegment} options={['概览', '详情', '日志']} />
                <Switch checked={dense} checkedChildren='紧凑' unCheckedChildren='舒适' onChange={setDense} />
              </Space>
            </Space>
          </Col>
          <Col xs={24} lg={11}>
            <div className={`mini-status-panel${dense ? ' is-dense' : ''}`}>
              <div className='mini-status-top'>
                <Avatar style={{ backgroundColor: primaryColor }}>UI</Avatar>
                <div>
                  <strong>组件状态卡片</strong>
                  <span>Segmented：{segment}</span>
                </div>
                <CheckCircleOutlined className='mini-status-icon' style={{ color: semantic.success }} />
              </div>
              <Progress percent={72} strokeColor={primaryColor} trailColor={withAlpha(primaryColor, 0.12)} />
              <Typography.Text type='secondary'>可参考此结构组合 Card、Avatar、Badge、Progress 与 Tag。</Typography.Text>
            </div>
          </Col>
        </Row>
      </Card>

      <Row gutter={[18, 18]} className='showcase-row'>
        <Col xs={24} xl={14}>
          <Card
            className='showcase-card'
            title={
              <Space>
                <LineChartOutlined />
                ECharts / BaseChart
              </Space>
            }
            extra={<Tag>响应式容器</Tag>}
          >
            <Typography.Paragraph type='secondary'>`BaseChart` 统一处理初始化、销毁、容器 resize、loading 与 empty 状态。</Typography.Paragraph>
            <BaseChart option={chartOption} height={280} />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card
            className='showcase-card'
            title={
              <Space>
                <DeploymentUnitOutlined />
                AntV G6
              </Space>
            }
            extra={<Tag>可拖拽 / 缩放</Tag>}
          >
            <Typography.Paragraph type='secondary'>组件封装了 G6 实例生命周期；图数据、样式和交互仅作为 options 传入。</Typography.Paragraph>
            <G6Graph options={graphOptions} height={280} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[18, 18]} className='showcase-row'>
        <Col xs={24} xl={9}>
          <Card className='showcase-card copy-showcase' title='CopyButton' extra={<CopyButton value='https://api.example.internal/v1/users' successMessage='示例地址已复制' />}>
            <Typography.Paragraph type='secondary'>把复制状态、浏览器权限错误与 Tooltip 统一封装，业务页面只传入文本即可。</Typography.Paragraph>
            <code>https://api.example.internal/v1/users</code>
            <Space className='copy-showcase-action'>
              <CopyButton type='primary' value={'curl -H "Authorization: Bearer <token>" https://api.example.internal/v1/users'} successMessage='curl 示例已复制'>
                复制请求示例
              </CopyButton>
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={15}>
          <Card className='showcase-card' title='CodeViewer · 快速预览' extra={<Tag color='blue'>完全本地加载</Tag>}>
            <Typography.Paragraph type='secondary'>Monaco 运行文件随应用发布，不请求 CDN；支持查找、字号、复制、下载和全屏。</Typography.Paragraph>
            <CodeViewer value={BASE_CHART_SNIPPET} language='typescript' filename='base-chart-demo.tsx' height={300} />
          </Card>
        </Col>
      </Row>

      <Card
        className='showcase-card showcase-section'
        title={
          <Space>
            <CodeOutlined />
            CodeViewer · 多语言完整演示
          </Space>
        }
        extra={<Badge status='success' text='同源静态资源 / 内网可用' />}
      >
        <Typography.Paragraph type='secondary'>
          切换语言可验证 Monaco 的语法高亮；所有示例均可编辑，并可使用工具栏完成查找、复制、下载与全屏。JSON 和 XML 还支持一键格式化。
        </Typography.Paragraph>
        <Tabs
          className='code-language-tabs'
          activeKey={activeCode}
          onChange={setActiveCode}
          items={CODE_SAMPLES.map((sample) => ({ key: sample.key, label: sample.label }))}
        />
        <CodeViewer
          key={currentCode.key}
          value={codeDrafts[currentCode.key]}
          language={currentCode.language}
          filename={currentCode.filename}
          readOnly={false}
          height={410}
          onChange={(value) => setCodeDrafts((current) => ({ ...current, [currentCode.key]: value }))}
        />
      </Card>

      <Card
        className='showcase-card showcase-section smart-table-showcase'
        title={
          <Space>
            <TableOutlined />
            SmartTable · 完整功能演示
          </Space>
        }
        extra={<Tag color='processing'>47 条本地 Mock 数据</Tag>}
      >
        <Typography.Paragraph type='secondary'>
          模拟真实服务端查询：搜索和筛选会回到第一页；点击列头可排序，表头漏斗可叠加过滤；分页、勾选、刷新、重置和 CSV 导出均可实际操作。
        </Typography.Paragraph>
        <SmartTable<DemoTableRow, DemoTableSearch>
          ref={tableRef}
          rowKey='id'
          columns={tableColumns}
          initialSearch={{ keyword: '', range: null }}
          request={async (params) => {
            await new Promise((resolve) => window.setTimeout(resolve, 280));
            const rows = applyTableQuery(params);
            const start = (params.page - 1) * params.pageSize;
            return { list: rows.slice(start, start + params.pageSize), total: rows.length };
          }}
          onRequestError={() => message.error('Mock 数据加载失败')}
          onExport={exportTable}
          toolbarClassName='smart-table-demo-toolbar'
          searchClassName='smart-table-demo-search'
          actionsClassName='smart-table-demo-actions'
          searchRender={({ search, setSearch, submit, reset }) => (
            <Space wrap size={[8, 8]}>
              <Input
                value={search.keyword}
                prefix={<SearchOutlined />}
                allowClear
                placeholder='姓名 / 编号'
                onChange={(event) => setSearch((current) => ({ ...current, keyword: event.target.value }))}
                onPressEnter={() => submit()}
                className='smart-table-keyword'
              />
              <Select
                value={search.department}
                allowClear
                placeholder='所属部门'
                options={DEPARTMENTS.map((value) => ({ value, label: value }))}
                onChange={(value) => setSearch((current) => ({ ...current, department: value }))}
                className='smart-table-filter'
              />
              <Select
                value={search.status}
                allowClear
                placeholder='账号状态'
                options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.label }))}
                onChange={(value) => setSearch((current) => ({ ...current, status: value as DemoStatus | undefined }))}
                className='smart-table-filter'
              />
              <DatePicker.RangePicker
                value={search.range}
                placeholder={['更新开始日', '更新结束日']}
                onChange={(value) => setSearch((current) => ({ ...current, range: value as [Dayjs, Dayjs] | null }))}
              />
              <Button type='primary' icon={<SearchOutlined />} onClick={() => submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
            </Space>
          )}
          toolbarRender={({ reload, exportData }) => (
            <Space wrap>
              <Typography.Text type='secondary'>已选 {selectedRowKeys.length} 项</Typography.Text>
              <Button icon={<ReloadOutlined />} onClick={() => reload()}>刷新</Button>
              <Button icon={<DownloadOutlined />} onClick={exportData}>导出结果</Button>
            </Space>
          )}
          rowSelection={{ selectedRowKeys, preserveSelectedRowKeys: true, onChange: setSelectedRowKeys }}
          pagination={{
            pageSize: 5,
            pageSizeOptions: [5, 10, 20],
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条 / 共 ${total} 条`,
          }}
          scroll={{ x: 980 }}
        />
      </Card>
    </div>
  );
}
