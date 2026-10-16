import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, DatePicker, Empty, Select, Space, Spin, Table, Tag, Tooltip, Typography } from 'antd';
import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import dayjs, { type Dayjs } from 'dayjs';
import { BaseChart } from '../../../components/BaseChart';
import { downloadBlob } from '../../../utils/download';
import { getApiErrorMessage } from '../../../utils/apiError';
import { getDimensionStatistics, getStatisticsDimensionOptions, getStatisticsEnvironments, getStatisticsTransactions } from './api';
import type { DimensionOption, DimensionStatisticsFilter, DimensionStatisticsResult, DimensionStatisticsRow, StatisticsDimensionType, StatisticsEnvironment, StatisticsTransaction } from './types';

const { RangePicker } = DatePicker;
const dimensionLabels: Record<StatisticsDimensionType, string> = { TRANSACTION: '交易维度', CALLER: '调用方系统维度', TRANSACTION_CALLER: '交易-调用方维度', SERVICE: '交易服务维度' };
const statusColors = { Y: '#52c41a', N: '#faad14', E: '#ff4d4f' };

type SearchState = {
  environmentId: string;
  monthRange: [Dayjs, Dayjs];
  dimensionType: StatisticsDimensionType;
  transactionCode?: string;
  callerId?: string;
  serviceId?: string;
};

const csv = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`;

function filterFrom(search: SearchState): DimensionStatisticsFilter {
  return { environmentId: search.environmentId, startMonth: search.monthRange[0].format('YYYY-MM'), endMonth: search.monthRange[1].format('YYYY-MM'), dimensionType: search.dimensionType, transactionCode: search.transactionCode, callerId: search.callerId, serviceId: search.serviceId };
}

export function DimensionAnalysis() {
  const { message } = App.useApp();
  const initialSearch = useMemo<SearchState>(() => ({ environmentId: 'sandbox', monthRange: [dayjs().subtract(5, 'month').startOf('month'), dayjs().startOf('month')], dimensionType: 'TRANSACTION' }), []);
  const [search, setSearch] = useState(initialSearch);
  const [environments, setEnvironments] = useState<StatisticsEnvironment[]>([]);
  const [transactions, setTransactions] = useState<StatisticsTransaction[]>([]);
  const [callers, setCallers] = useState<DimensionOption[]>([]);
  const [services, setServices] = useState<DimensionOption[]>([]);
  const [result, setResult] = useState<DimensionStatisticsResult>();
  const [selectedRow, setSelectedRow] = useState<DimensionStatisticsRow>();
  const [loading, setLoading] = useState(true);
  const [querying, setQuerying] = useState(false);
  const [error, setError] = useState<string>();

  const query = async (nextSearch: SearchState, initial = false) => {
    if (initial) setLoading(true);
    else setQuerying(true);
    setError(undefined);
    try {
      const data = await getDimensionStatistics(filterFrom(nextSearch));
      setResult(data);
      setSelectedRow(data.rows[0]);
    } catch (cause) {
      setResult(undefined);
      setSelectedRow(undefined);
      setError(getApiErrorMessage(cause, '无法加载维度统计数据'));
    } finally {
      if (initial) setLoading(false);
      else setQuerying(false);
    }
  };

  useEffect(() => {
    void Promise.all([getStatisticsEnvironments(), getStatisticsTransactions(), getStatisticsDimensionOptions()])
      .then(([environmentList, transactionList, options]) => {
        setEnvironments(environmentList);
        setTransactions(transactionList);
        setCallers(options.callers);
        setServices(options.services);
        const nextSearch = { ...initialSearch, environmentId: environmentList[0]?.id ?? initialSearch.environmentId };
        setSearch(nextSearch);
        return query(nextSearch, true);
      })
      .catch((cause) => { setError(getApiErrorMessage(cause, '无法初始化维度分析页面')); setLoading(false); });
    // Initial query is explicit; changing filters waits for the user to submit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialSearch]);

  const columns = useMemo<ColumnsType<DimensionStatisticsRow>>(() => {
    const identityColumns: ColumnsType<DimensionStatisticsRow> = search.dimensionType === 'TRANSACTION'
      ? [{ title: '交易码', dataIndex: 'transactionCode', width: 220 }, { title: '交易名称', dataIndex: 'transactionName', width: 190 }]
      : search.dimensionType === 'CALLER'
        ? [{ title: '调用系统 ID', dataIndex: 'callerId', width: 180 }, { title: '调用系统名称', dataIndex: 'callerName', width: 180 }]
        : search.dimensionType === 'SERVICE'
          ? [{ title: '服务标识', dataIndex: 'serviceId', width: 190 }, { title: '服务名称', dataIndex: 'serviceName', width: 190 }]
          : [{ title: '交易码', dataIndex: 'transactionCode', width: 210 }, { title: '交易名称', dataIndex: 'transactionName', width: 170 }, { title: '调用系统 ID', dataIndex: 'callerId', width: 180 }, { title: '调用系统名称', dataIndex: 'callerName', width: 170 }];
    return [
      ...identityColumns,
      { title: '成功 Y', dataIndex: 'successCount', width: 100, align: 'right', sorter: (left, right) => left.successCount - right.successCount, render: (value) => <Tag color='success'>{value}</Tag> },
      { title: '查无数据 N', dataIndex: 'noDataCount', width: 110, align: 'right', sorter: (left, right) => left.noDataCount - right.noDataCount, render: (value) => <Tag color='warning'>{value}</Tag> },
      { title: '失败 E', dataIndex: 'failureCount', width: 100, align: 'right', sorter: (left, right) => left.failureCount - right.failureCount, render: (value) => <Tag color='error'>{value}</Tag> },
      { title: '总笔数', dataIndex: 'totalCount', width: 100, align: 'right', defaultSortOrder: 'descend', sorter: (left, right) => left.totalCount - right.totalCount },
      { title: '成功率', dataIndex: 'successRate', width: 110, align: 'right', sorter: (left, right) => left.successRate - right.successRate, render: (value) => `${value}%` },
      { title: '环比变化', dataIndex: 'monthOnMonthRate', width: 130, align: 'right', sorter: (left, right) => (left.monthOnMonthRate ?? Number.NEGATIVE_INFINITY) - (right.monthOnMonthRate ?? Number.NEGATIVE_INFINITY), render: (value: number | undefined, row) => value === undefined && row.previousTotalCount === 0 ? <Tag color='blue'>新增</Tag> : <Typography.Text type={value && value < 0 ? 'danger' : undefined}>{value === undefined ? '-' : `${value > 0 ? '+' : ''}${value}%`}</Typography.Text> },
    ];
  }, [search.dimensionType]);

  const chartOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功 Y', '查无数据 N', '失败 E'] },
    grid: { left: 42, right: 24, top: 48, bottom: 68 },
    xAxis: { type: 'category', axisLabel: { rotate: 24 }, data: result?.chartItems.map((item) => item.label) ?? [] },
    yAxis: { type: 'value', name: '笔数', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
    series: [
      { name: '成功 Y', type: 'bar', stack: 'status', itemStyle: { color: statusColors.Y }, data: result?.chartItems.map((item) => ({ value: item.successCount, itemStyle: { opacity: !selectedRow || item.id === selectedRow.id ? 1 : 0.35 } })) ?? [] },
      { name: '查无数据 N', type: 'bar', stack: 'status', itemStyle: { color: statusColors.N }, data: result?.chartItems.map((item) => ({ value: item.noDataCount, itemStyle: { opacity: !selectedRow || item.id === selectedRow.id ? 1 : 0.35 } })) ?? [] },
      { name: '失败 E', type: 'bar', stack: 'status', itemStyle: { color: statusColors.E }, data: result?.chartItems.map((item) => ({ value: item.failureCount, itemStyle: { opacity: !selectedRow || item.id === selectedRow.id ? 1 : 0.35 } })) ?? [] },
    ],
  }), [result, selectedRow]);

  const exportRows = () => {
    const rows = result?.rows ?? [];
    if (!rows.length) return message.warning('没有可导出的维度统计数据');
    const header = ['维度', '交易码', '交易名称', '调用系统 ID', '调用系统名称', '服务标识', '服务名称', '成功 Y', '查无数据 N', '失败 E', '总笔数', '成功率', '环比变化'];
    const lines = rows.map((row) => [dimensionLabels[search.dimensionType], row.transactionCode, row.transactionName, row.callerId, row.callerName, row.serviceId, row.serviceName, row.successCount, row.noDataCount, row.failureCount, row.totalCount, `${row.successRate}%`, row.monthOnMonthRate === undefined ? (row.previousTotalCount === 0 ? '新增' : '') : `${row.monthOnMonthRate}%`].map(csv).join(','));
    downloadBlob(new Blob([`\uFEFF${[header.map(csv).join(','), ...lines].join('\r\n')}`], { type: 'text/csv;charset=utf-8' }), `statistics-${search.dimensionType.toLowerCase()}-${dayjs().format('YYYYMMDD')}.csv`);
    message.success(`已导出 ${rows.length} 条维度统计数据`);
  };

  if (loading) return <Spin fullscreen tip='正在加载维度统计配置' />;

  return <>
    <Card className='statistics-filter-card'>
      <Space wrap size='middle'>
        <Select value={search.dimensionType} style={{ width: 180 }} options={(Object.keys(dimensionLabels) as StatisticsDimensionType[]).map((value) => ({ value, label: dimensionLabels[value] }))} onChange={(value) => setSearch((current) => ({ ...current, dimensionType: value }))} />
        <Select value={search.environmentId} style={{ width: 160 }} options={environments.map((item) => ({ value: item.id, label: item.name }))} onChange={(value) => setSearch((current) => ({ ...current, environmentId: value }))} />
        <RangePicker picker='month' value={search.monthRange} allowClear={false} onChange={(value) => { const [startMonth, endMonth] = value ?? []; if (startMonth && endMonth) setSearch((current) => ({ ...current, monthRange: [startMonth, endMonth] })); }} />
        <Select value={search.transactionCode} allowClear showSearch optionFilterProp='label' placeholder='全部交易' style={{ width: 280 }} options={transactions.map((item) => ({ value: item.transactionCode, label: `${item.transactionCode} - ${item.transactionName}` }))} onChange={(value) => setSearch((current) => ({ ...current, transactionCode: value }))} />
        <Select value={search.callerId} allowClear placeholder='全部调用系统' style={{ width: 210 }} options={callers.map((item) => ({ value: item.id, label: item.label }))} onChange={(value) => setSearch((current) => ({ ...current, callerId: value }))} />
        <Select value={search.serviceId} allowClear placeholder='全部交易服务' style={{ width: 220 }} options={services.map((item) => ({ value: item.id, label: item.label }))} onChange={(value) => setSearch((current) => ({ ...current, serviceId: value }))} />
        <Button type='primary' icon={<SearchOutlined />} loading={querying} onClick={() => void query(search)}>查询</Button>
        <Button icon={<ReloadOutlined />} onClick={() => { setSearch(initialSearch); void query(initialSearch); }}>重置</Button>
      </Space>
    </Card>
    {error && <Alert className='statistics-alert' type='error' showIcon message='维度统计加载失败' description={error} closable onClose={() => setError(undefined)} />}
    <Spin spinning={querying}>
      {result?.rows.length ? <div className='statistics-dimension-grid'>
        <Card title={`${dimensionLabels[search.dimensionType]}分布`} extra={<Tooltip title='点击表格行可高亮对应柱状分布'><Tag color='processing'>表格联动图表</Tag></Tooltip>}><BaseChart option={chartOption} height={360} /></Card>
        <Card title='维度统计明细' extra={<Button icon={<DownloadOutlined />} onClick={exportRows}>导出</Button>}>
          <Table rowKey='id' size='small' columns={columns} dataSource={result.rows} scroll={{ x: 1000 }} pagination={{ pageSize: 6, showSizeChanger: false, showTotal: (total) => `共 ${total} 条` }} rowClassName={(row) => row.id === selectedRow?.id ? 'statistics-selected-row' : ''} onRow={(row) => ({ onClick: () => setSelectedRow(row) })} />
        </Card>
      </div> : <Card><Empty description='当前筛选条件下没有维度统计数据' /></Card>}
    </Spin>
  </>;
}
