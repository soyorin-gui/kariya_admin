import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, DatePicker, Empty, Select, Space, Spin, Table, Tag, Tooltip, Typography } from 'antd';
import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import dayjs, { type Dayjs } from 'dayjs';
import { BaseChart } from '../../../components/BaseChart';
import { downloadBlob } from '../../../utils/download';
import { getApiErrorMessage } from '../../../utils/apiError';
import { getRankingStatistics, getStatisticsEnvironments } from './api';
import type { RankingStatisticsFilter, RankingStatisticsResult, RankingStatisticsRow, StatisticsEnvironment, StatisticsRankingDimension, StatisticsRankingMetric, StatisticsRankingOrder } from './types';

type SearchState = {
  environmentId: string;
  month: Dayjs;
  metric: StatisticsRankingMetric;
  dimension: StatisticsRankingDimension;
  order: StatisticsRankingOrder;
};

const metricLabels: Record<StatisticsRankingMetric, string> = { VOLUME: '调用量', GROWTH: '调用量增幅', AVG_ELAPSED_TIME: '平均响应时间' };
const dimensionLabels: Record<StatisticsRankingDimension, string> = { TRANSACTION: '交易', CALLER: '调用系统' };
const abbreviate = (value: string, length = 15) => value.length > length ? `${value.slice(0, length)}…` : value;

function toFilter(search: SearchState): RankingStatisticsFilter {
  return { environmentId: search.environmentId, month: search.month.format('YYYY-MM'), metric: search.metric, dimension: search.dimension, order: search.order };
}

export function RankingAnalysis() {
  const { message } = App.useApp();
  const initialSearch = useMemo<SearchState>(() => ({ environmentId: 'sandbox', month: dayjs().startOf('month'), metric: 'VOLUME', dimension: 'TRANSACTION', order: 'DESC' }), []);
  const [search, setSearch] = useState(initialSearch);
  const [environments, setEnvironments] = useState<StatisticsEnvironment[]>([]);
  const [result, setResult] = useState<RankingStatisticsResult>();
  const [selectedRowId, setSelectedRowId] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [querying, setQuerying] = useState(false);
  const [error, setError] = useState<string>();

  const query = async (nextSearch: SearchState, initial = false) => {
    if (initial) setLoading(true);
    else setQuerying(true);
    setError(undefined);
    try {
      const data = await getRankingStatistics(toFilter(nextSearch));
      setResult(data);
      setSelectedRowId(undefined);
    } catch (cause) {
      setResult(undefined);
      setError(getApiErrorMessage(cause, '无法加载排行统计数据'));
    } finally {
      if (initial) setLoading(false);
      else setQuerying(false);
    }
  };

  useEffect(() => {
    void getStatisticsEnvironments()
      .then((environmentList) => {
        setEnvironments(environmentList);
        const nextSearch = { ...initialSearch, environmentId: environmentList[0]?.id ?? initialSearch.environmentId };
        setSearch(nextSearch);
        return query(nextSearch, true);
      })
      .catch((cause) => { setError(getApiErrorMessage(cause, '无法初始化排行分析页面')); setLoading(false); });
    // Initial query is explicit; changing filters waits for the user to submit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialSearch]);

  const rows = result?.rows ?? [];
  const rankingBarOption = useMemo<EChartsOption>(() => {
    const unit = search.metric === 'AVG_ELAPSED_TIME' ? ' ms' : search.metric === 'GROWTH' ? '%' : ' 笔';
    return {
      grid: { left: 130, right: 32, top: 24, bottom: 24 },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => value === undefined || value === null ? '不计算' : `${value}${unit}` },
      xAxis: { type: 'value', axisLabel: { formatter: `{value}${unit}` } },
      yAxis: { type: 'category', inverse: true, data: rows.map((row) => abbreviate(row.dimensionName)), axisLabel: { width: 115, overflow: 'truncate' } },
      series: [{
        type: 'bar',
        barMaxWidth: 22,
        data: rows.map((row) => ({
          value: search.metric === 'VOLUME' ? row.totalCount : search.metric === 'GROWTH' ? row.growthRate : row.averageElapsedTime,
          itemStyle: { color: !selectedRowId || selectedRowId === row.id ? '#1677ff' : 'rgba(22, 119, 255, .22)' },
        })),
      }],
    };
  }, [rows, search.metric, selectedRowId]);

  const growthComparisonOption = useMemo<EChartsOption>(() => {
    const comparison = result?.growthComparison ?? [];
    return {
      color: ['#1677ff', '#b7c6df'],
      grid: { left: 130, right: 32, top: 24, bottom: 24 },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['本月调用量', '上月调用量'] },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', inverse: true, data: comparison.map((row) => abbreviate(row.dimensionName)), axisLabel: { width: 115, overflow: 'truncate' } },
      series: [
        { name: '本月调用量', type: 'bar', barMaxWidth: 18, data: comparison.map((row) => row.totalCount) },
        { name: '上月调用量', type: 'bar', barMaxWidth: 18, data: comparison.map((row) => row.previousTotalCount) },
      ],
    };
  }, [result?.growthComparison]);

  const columns = useMemo<ColumnsType<RankingStatisticsRow>>(() => [
    { title: '排名', dataIndex: 'rank', width: 72, align: 'center' },
    { title: `${dimensionLabels[search.dimension]}编码`, dataIndex: 'dimensionCode', width: 180, ellipsis: true },
    {
      title: `${dimensionLabels[search.dimension]}名称`, dataIndex: 'dimensionName', width: 260,
      render: (value: string) => <Tooltip title={value}><Typography.Text ellipsis={{ tooltip: false }} className='statistics-ranking-name'>{value}</Typography.Text></Tooltip>,
    },
    { title: '本月调用量', dataIndex: 'totalCount', width: 128, align: 'right' },
    { title: '上月调用量', dataIndex: 'previousTotalCount', width: 128, align: 'right' },
    {
      title: '调用量增幅', dataIndex: 'growthRate', width: 132, align: 'right',
      render: (value: number | undefined, row) => value === undefined ? row.previousTotalCount === 0 ? <Tag color='blue'>新增</Tag> : '-' : `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`,
    },
    { title: '平均响应时间', dataIndex: 'averageElapsedTime', width: 140, align: 'right', render: (value: number) => `${value} ms` },
    { title: '响应样本数', dataIndex: 'elapsedTimeSampleCount', width: 124, align: 'right' },
  ], [search.dimension]);

  const exportRows = () => {
    if (rows.length === 0) {
      message.warning('当前没有可导出的排行数据');
      return;
    }
    const lines = [
      ['排名', '维度编码', '维度名称', '本月调用量', '上月调用量', '调用量增幅', '平均响应时间(ms)', '响应样本数'],
      ...rows.map((row) => [row.rank, row.dimensionCode, row.dimensionName, row.totalCount, row.previousTotalCount, row.growthRate === undefined ? '不计算（上月为 0）' : `${row.growthRate.toFixed(2)}%`, row.averageElapsedTime, row.elapsedTimeSampleCount]),
    ];
    const csv = lines.map((line) => line.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n');
    downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), '交易统计排行.csv');
    message.success('排行明细已导出');
  };

  if (loading) return <Spin fullscreen tip='正在加载排行统计配置' />;

  return <Space direction='vertical' size={16} className='statistics-content'>
    <Card className='statistics-filter-card'>
      <Space size={[12, 12]} wrap>
        <Select value={search.environmentId} style={{ width: 160 }} options={environments.map((item) => ({ label: item.name, value: item.id }))} onChange={(value) => setSearch((current) => ({ ...current, environmentId: value }))} placeholder='运行环境' />
        <DatePicker picker='month' allowClear={false} value={search.month} onChange={(value) => value && setSearch((current) => ({ ...current, month: value }))} />
        <Select value={search.metric} style={{ width: 160 }} options={Object.entries(metricLabels).map(([value, label]) => ({ value, label }))} onChange={(value) => setSearch((current) => ({ ...current, metric: value }))} />
        <Select value={search.dimension} style={{ width: 150 }} options={Object.entries(dimensionLabels).map(([value, label]) => ({ value, label }))} onChange={(value) => setSearch((current) => ({ ...current, dimension: value }))} />
        <Select value={search.order} style={{ width: 130 }} options={[{ value: 'DESC', label: '倒序 TOP10' }, { value: 'ASC', label: '正序 TOP10' }]} onChange={(value) => setSearch((current) => ({ ...current, order: value }))} />
        <Button type='primary' icon={<SearchOutlined />} loading={querying} onClick={() => void query(search)}>查询</Button>
        <Button icon={<ReloadOutlined />} onClick={() => { setSearch(initialSearch); void query(initialSearch); }}>重置</Button>
      </Space>
      <Typography.Text type='secondary' className='statistics-filter-hint'>上月调用量为 0 的记录标识为“新增”，不计算虚假增幅。</Typography.Text>
    </Card>
    {error && <Alert type='error' showIcon message='排行数据加载失败' description={error} closable onClose={() => setError(undefined)} />}
    <Spin spinning={querying}>
      {!error && rows.length === 0 ? <Card><Empty description='暂无符合条件的排行数据' /></Card> : null}
      {rows.length > 0 && <>
        <div className='statistics-ranking-grid'>
          <Card title={`${metricLabels[search.metric]} TOP10`} extra={<Tag color='blue'>{search.order === 'DESC' ? '倒序' : '正序'}</Tag>}><BaseChart option={rankingBarOption} height={350} empty={false} /></Card>
          <Card title='调用量同比较' extra={<Typography.Text type='secondary'>仅展示可计算增幅的记录</Typography.Text>}><BaseChart option={growthComparisonOption} height={350} empty={false} /></Card>
        </div>
        <Card className='statistics-ranking-table-card' title='排行明细' extra={<Button icon={<DownloadOutlined />} onClick={exportRows}>导出</Button>}>
          <Table rowKey='id' columns={columns} dataSource={rows} pagination={false} scroll={{ x: 1180 }} onRow={(row) => ({ onClick: () => setSelectedRowId(row.id) })} rowClassName={(row) => row.id === selectedRowId ? 'statistics-selected-row' : ''} />
        </Card>
      </>}
    </Spin>
  </Space>;
}
