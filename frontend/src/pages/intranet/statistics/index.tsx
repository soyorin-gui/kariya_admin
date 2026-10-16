import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, DatePicker, Empty, Select, Space, Spin, Statistic, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { BarChartOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import dayjs, { type Dayjs } from 'dayjs';
import { BaseChart } from '../../../components/BaseChart';
import { getApiErrorMessage } from '../../../utils/apiError';
import { getStatisticsEnvironments, getStatisticsOverview, getStatisticsTransactions } from './api';
import { DimensionAnalysis } from './DimensionAnalysis';
import { RankingAnalysis } from './RankingAnalysis';
import type { DailyStatisticsPoint, StatisticsEnvironment, StatisticsFilter, StatisticsOverview, StatisticsTransaction } from './types';
import './index.css';

const { RangePicker } = DatePicker;

type SearchState = {
  environmentId: string;
  dateRange: [Dayjs, Dayjs];
  hourDate: Dayjs;
  transactionCode?: string;
};

const statusColors = { Y: '#52c41a', N: '#faad14', E: '#ff4d4f', TOTAL: '#1677ff', ELAPSED: '#722ed1' };

function toFilter(search: SearchState): StatisticsFilter {
  return {
    environmentId: search.environmentId,
    startDate: search.dateRange[0].format('YYYY-MM-DD'),
    endDate: search.dateRange[1].format('YYYY-MM-DD'),
    hourDate: search.hourDate.format('YYYY-MM-DD'),
    transactionCode: search.transactionCode,
  };
}

function statusSeries(points: Array<{ successCount: number; noDataCount: number; failureCount: number; totalCount: number }>) {
  return [
    { name: '成功 Y', type: 'bar' as const, stack: 'status', itemStyle: { color: statusColors.Y }, data: points.map((item) => item.successCount) },
    { name: '查无数据 N', type: 'bar' as const, stack: 'status', itemStyle: { color: statusColors.N }, data: points.map((item) => item.noDataCount) },
    { name: '失败 E', type: 'bar' as const, stack: 'status', itemStyle: { color: statusColors.E }, data: points.map((item) => item.failureCount) },
    { name: '总笔数', type: 'line' as const, smooth: true, itemStyle: { color: statusColors.TOTAL }, data: points.map((item) => item.totalCount) },
  ];
}

export default function TransactionStatisticsPage() {
  const { message } = App.useApp();
  const initialSearch = useMemo<SearchState>(() => ({ environmentId: 'sandbox', dateRange: [dayjs().subtract(6, 'day'), dayjs()], hourDate: dayjs(), transactionCode: undefined }), []);
  const [activeTab, setActiveTab] = useState('overview');
  const [search, setSearch] = useState<SearchState>(initialSearch);
  const [environments, setEnvironments] = useState<StatisticsEnvironment[]>([]);
  const [transactions, setTransactions] = useState<StatisticsTransaction[]>([]);
  const [overview, setOverview] = useState<StatisticsOverview>();
  const [loading, setLoading] = useState(true);
  const [querying, setQuerying] = useState(false);
  const [error, setError] = useState<string>();

  const loadOverview = async (nextSearch: SearchState, initial = false) => {
    if (initial) setLoading(true);
    else setQuerying(true);
    setError(undefined);
    try {
      setOverview(await getStatisticsOverview(toFilter(nextSearch)));
    } catch (cause) {
      setOverview(undefined);
      setError(getApiErrorMessage(cause, '无法加载交易统计数据'));
    } finally {
      if (initial) setLoading(false);
      else setQuerying(false);
    }
  };

  useEffect(() => {
    void Promise.all([getStatisticsEnvironments(), getStatisticsTransactions()])
      .then(([environmentList, transactionList]) => {
        setEnvironments(environmentList);
        setTransactions(transactionList);
        const nextSearch = { ...initialSearch, environmentId: environmentList[0]?.id ?? initialSearch.environmentId };
        setSearch(nextSearch);
        return loadOverview(nextSearch, true);
      })
      .catch((cause) => {
        setError(getApiErrorMessage(cause, '无法初始化交易统计页面'));
        setLoading(false);
      });
    // Initial loading is intentionally explicit; changing filters only queries after the user clicks “统计”.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialSearch]);

  const dailyOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功 Y', '查无数据 N', '失败 E', '总笔数'] },
    grid: { left: 44, right: 24, top: 48, bottom: 44 },
    xAxis: { type: 'category', boundaryGap: true, data: overview?.dailyPoints.map((item) => item.date.slice(5)) ?? [] },
    yAxis: { type: 'value', name: '笔数', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
    series: statusSeries(overview?.dailyPoints ?? []),
  }), [overview]);

  const elapsedOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value} ms` },
    legend: { data: ['平均响应时间'] },
    grid: { left: 54, right: 24, top: 48, bottom: 44 },
    xAxis: { type: 'category', data: overview?.dailyPoints.map((item) => item.date.slice(5)) ?? [] },
    yAxis: { type: 'value', name: 'ms', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
    series: [{ name: '平均响应时间', type: 'line', smooth: true, symbolSize: 7, itemStyle: { color: statusColors.ELAPSED }, areaStyle: { color: 'rgba(114, 46, 209, .10)' }, data: overview?.dailyPoints.map((item) => item.averageElapsedTime) ?? [] }],
  }), [overview]);

  const monthlyOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功 Y', '查无数据 N', '失败 E', '总笔数'] },
    grid: { left: 44, right: 24, top: 48, bottom: 56 },
    xAxis: { type: 'category', data: overview?.monthlyPoints.map((item) => item.isCurrentMonth ? `${item.month.slice(5)}（本月累计）` : item.month.slice(5)) ?? [] },
    yAxis: { type: 'value', name: '笔数', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
    series: statusSeries(overview?.monthlyPoints ?? []),
  }), [overview]);

  const hourlyOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis' },
    legend: { data: ['调用量', '平均响应时间'] },
    grid: { left: 50, right: 48, top: 48, bottom: 44 },
    xAxis: { type: 'category', data: overview?.hourlyPoints.map((item) => item.hour) ?? [] },
    yAxis: [
      { type: 'value', name: '调用量', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
      { type: 'value', name: 'ms' },
    ],
    series: [
      { name: '调用量', type: 'bar', itemStyle: { color: statusColors.TOTAL }, data: overview?.hourlyPoints.map((item) => item.totalCount) ?? [] },
      { name: '平均响应时间', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: statusColors.ELAPSED }, data: overview?.hourlyPoints.map((item) => item.averageElapsedTime) ?? [] },
    ],
  }), [overview]);

  const dailyColumns: ColumnsType<DailyStatisticsPoint> = [
    { title: '统计日期', dataIndex: 'date', width: 130 },
    { title: '成功 Y', dataIndex: 'successCount', align: 'right', render: (value) => <Tag color='success'>{value}</Tag> },
    { title: '查无数据 N', dataIndex: 'noDataCount', align: 'right', render: (value) => <Tag color='warning'>{value}</Tag> },
    { title: '失败 E', dataIndex: 'failureCount', align: 'right', render: (value) => <Tag color='error'>{value}</Tag> },
    { title: '总笔数', dataIndex: 'totalCount', align: 'right' },
    { title: '平均响应时间', dataIndex: 'averageElapsedTime', align: 'right', render: (value) => `${value} ms` },
  ];

  const overviewContent = <>
    <Card className='statistics-filter-card'>
      <Space wrap size='middle'>
        <Select value={search.environmentId} style={{ width: 160 }} placeholder='运行环境' options={environments.map((item) => ({ value: item.id, label: item.name }))} onChange={(value) => setSearch((current) => ({ ...current, environmentId: value }))} />
        <RangePicker value={search.dateRange} allowClear={false} onChange={(value) => {
          const [startDate, endDate] = value ?? [];
          if (!startDate || !endDate) return;
          setSearch((current) => ({ ...current, dateRange: [startDate, endDate], hourDate: endDate }));
        }} />
        <DatePicker value={search.hourDate} allowClear={false} placeholder='小时下钻日期' onChange={(value) => value && setSearch((current) => ({ ...current, hourDate: value }))} />
        <Select value={search.transactionCode} allowClear showSearch optionFilterProp='label' placeholder='全部交易' style={{ width: 280 }} options={transactions.map((item) => ({ value: item.transactionCode, label: `${item.transactionCode} - ${item.transactionName}` }))} onChange={(value) => setSearch((current) => ({ ...current, transactionCode: value }))} />
        <Button type='primary' icon={<SearchOutlined />} loading={querying} onClick={() => void loadOverview(search)}>统计</Button>
        <Button icon={<ReloadOutlined />} onClick={() => { setSearch(initialSearch); void loadOverview(initialSearch); }}>重置</Button>
      </Space>
    </Card>
    {error && <Alert className='statistics-alert' type='error' showIcon message='统计加载失败' description={error} closable onClose={() => setError(undefined)} />}
    <Spin spinning={querying}>
      <div className='statistics-kpi-grid'>
        {[{ key: 'totalCount', title: '总交易量', color: 'var(--brand)' }, { key: 'successCount', title: '成功 Y', color: statusColors.Y }, { key: 'noDataCount', title: '查无数据 N', color: statusColors.N }, { key: 'failureCount', title: '失败 E', color: statusColors.E }].map((item) => <Card key={item.key} className='statistics-kpi-card'><Statistic title={item.title} value={overview?.[item.key as keyof StatisticsOverview] as number ?? 0} valueStyle={{ color: item.color }} /></Card>)}
      </div>
      {overview?.totalCount === 0 ? <Card><Empty description='当前筛选条件下没有交易统计数据' /></Card> : <>
        <div className='statistics-chart-grid'>
          <Card title='每日状态与总笔数趋势'><BaseChart option={dailyOption} empty={!overview?.dailyPoints.length} height={330} /></Card>
          <Card title='每日平均响应时间趋势' extra={<Tooltip title={`参与计算笔数：${overview?.elapsedTimeSampleCount ?? 0}`}><Tag color='purple'>平均 {overview?.averageElapsedTime ?? 0} ms</Tag></Tooltip>}><BaseChart option={elapsedOption} empty={!overview?.dailyPoints.length} height={330} /></Card>
          <Card title='最近六个月月度趋势' extra={<Tag color='processing'>本月累计已标注</Tag>}><BaseChart option={monthlyOption} empty={!overview?.monthlyPoints.length} height={330} /></Card>
          <Card title={`24 小时调用情况 · ${search.hourDate.format('YYYY-MM-DD')}`}><BaseChart option={hourlyOption} empty={!overview?.hourlyPoints.length} height={330} /></Card>
        </div>
        <Card title='每日 Y / N / E 汇总' className='statistics-daily-card'><Table rowKey='date' size='small' columns={dailyColumns} dataSource={overview?.dailyPoints} pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (total) => `共 ${total} 天` }} scroll={{ x: 760 }} /></Card>
      </>}
    </Spin>
  </>;

  if (loading) return <Spin fullscreen tip='正在加载交易统计配置' />;

  return <div className='page-fill statistics-page'>
    <div className='page-head'>
      <div><div className='page-kicker'>TRANSACTION STATISTICS</div><h1 className='page-title'>交易统计分析</h1></div>
      <Button type='primary' icon={<BarChartOutlined />} onClick={() => window.open('/statistics-screen', 'transaction-statistics-screen', 'noopener,noreferrer')}>打开监控大屏</Button>
    </div>
    <Tabs className='statistics-tabs' activeKey={activeTab} onChange={setActiveTab} items={[
      { key: 'overview', label: '概览与趋势', children: overviewContent },
      { key: 'dimension', label: '维度分析', children: <DimensionAnalysis /> },
      { key: 'ranking', label: '排行分析', children: <RankingAnalysis /> },
    ]} />
  </div>;
}
