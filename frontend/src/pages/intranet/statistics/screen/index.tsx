import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, DatePicker, Empty, Select, Spin, Statistic, Table, Tag, Tooltip, Typography } from 'antd';
import { FullscreenExitOutlined, FullscreenOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import dayjs, { type Dayjs } from 'dayjs';
import { BaseChart } from '../../../../components/BaseChart';
import { useFullscreen } from '../../../../hooks/useFullscreen';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { getStatisticsEnvironments, getStatisticsScreenSnapshot } from '../api';
import type { StatisticsEnvironment, StatisticsScreenFilter, StatisticsScreenSnapshot, StatisticsScreenTopTransaction } from '../types';
import './index.css';

const REFRESH_INTERVAL_MS = 60_000;
const statusColor = { Y: '#24e1a5', N: '#47a9ff', E: '#ff6b82', TOTAL: '#6c8cff', ELAPSED: '#b98cff' };

type SearchState = { environmentId: string; statisticsDate: Dayjs };

function toFilter(search: SearchState): StatisticsScreenFilter {
  return { environmentId: search.environmentId, statisticsDate: search.statisticsDate.format('YYYY-MM-DD') };
}

export default function TransactionStatisticsScreenPage() {
  const rootRef = useRef<HTMLDivElement>(null);
  const { fullscreen, toggleFullscreen } = useFullscreen(rootRef);
  const initialSearch = useMemo<SearchState>(() => ({ environmentId: 'sandbox', statisticsDate: dayjs() }), []);
  const [search, setSearch] = useState(initialSearch);
  const [environments, setEnvironments] = useState<StatisticsEnvironment[]>([]);
  const [snapshot, setSnapshot] = useState<StatisticsScreenSnapshot>();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();

  const loadSnapshot = async (nextSearch: SearchState, initial = false) => {
    if (initial) setLoading(true);
    else setRefreshing(true);
    setError(undefined);
    try {
      setSnapshot(await getStatisticsScreenSnapshot(toFilter(nextSearch)));
    } catch (cause) {
      setError(getApiErrorMessage(cause, '无法加载交易监控快照'));
    } finally {
      if (initial) setLoading(false);
      else setRefreshing(false);
    }
  };

  useEffect(() => {
    void getStatisticsEnvironments()
      .then((environmentList) => {
        setEnvironments(environmentList);
        const nextSearch = { ...initialSearch, environmentId: environmentList[0]?.id ?? initialSearch.environmentId };
        setSearch(nextSearch);
        return loadSnapshot(nextSearch, true);
      })
      .catch((cause) => { setError(getApiErrorMessage(cause, '无法初始化交易监控大屏')); setLoading(false); });
    // Initial loading is intentionally explicit; user filters and timed refreshes share loadSnapshot.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialSearch]);

  useEffect(() => {
    const timer = window.setInterval(() => void loadSnapshot(search), REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
    // The timer follows submitted display conditions without introducing a separate data source.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const trendOption = useMemo<EChartsOption>(() => ({
    color: [statusColor.Y, statusColor.N, statusColor.E, statusColor.TOTAL],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#a9c8ef' }, data: ['成功 Y', '查无数据 N', '失败 E', '总笔数'] },
    grid: { left: 42, right: 24, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: snapshot?.sevenDayTrend.map((item) => item.date) ?? [], axisLine: { lineStyle: { color: '#31557f' } }, axisLabel: { color: '#91afd5' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(111, 165, 224, .12)' } }, axisLabel: { color: '#91afd5' } },
    series: [
      { name: '成功 Y', type: 'bar', stack: 'status', data: snapshot?.sevenDayTrend.map((item) => item.successCount) ?? [] },
      { name: '查无数据 N', type: 'bar', stack: 'status', data: snapshot?.sevenDayTrend.map((item) => item.noDataCount) ?? [] },
      { name: '失败 E', type: 'bar', stack: 'status', data: snapshot?.sevenDayTrend.map((item) => item.failureCount) ?? [] },
      { name: '总笔数', type: 'line', smooth: true, symbolSize: 6, data: snapshot?.sevenDayTrend.map((item) => item.totalCount) ?? [] },
    ],
  }), [snapshot]);

  const statusOption = useMemo<EChartsOption>(() => ({
    color: [statusColor.Y, statusColor.N, statusColor.E],
    tooltip: { trigger: 'item', valueFormatter: (value) => `${value} 笔` },
    legend: { bottom: 0, textStyle: { color: '#a9c8ef' } },
    series: [{
      type: 'pie', radius: ['48%', '72%'], center: ['50%', '45%'], label: { color: '#d9ebff', formatter: '{b}\n{d}%' },
      data: [
        { name: '成功 Y', value: snapshot?.successCount ?? 0 },
        { name: '查无数据 N', value: snapshot?.noDataCount ?? 0 },
        { name: '失败 E', value: snapshot?.failureCount ?? 0 },
      ],
    }],
  }), [snapshot]);

  const topOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${value} 笔` },
    grid: { left: 150, right: 24, top: 14, bottom: 18 },
    xAxis: { type: 'value', axisLabel: { color: '#91afd5' }, splitLine: { lineStyle: { color: 'rgba(111, 165, 224, .12)' } } },
    yAxis: { type: 'category', inverse: true, data: snapshot?.topTransactions.map((item) => item.transactionName) ?? [], axisLabel: { color: '#a9c8ef', width: 132, overflow: 'truncate' } },
    series: [{ type: 'bar', barMaxWidth: 18, itemStyle: { color: '#3f8cff' }, data: snapshot?.topTransactions.map((item) => item.totalCount) ?? [] }],
  }), [snapshot]);

  const hourlyOption = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value} 笔` },
    grid: { left: 42, right: 24, top: 18, bottom: 28 },
    xAxis: { type: 'category', data: snapshot?.hourlyCounts.map((item) => item.hour) ?? [], axisLabel: { color: '#91afd5', interval: 2 }, axisLine: { lineStyle: { color: '#31557f' } } },
    yAxis: { type: 'value', axisLabel: { color: '#91afd5' }, splitLine: { lineStyle: { color: 'rgba(111, 165, 224, .12)' } } },
    series: [{ type: 'line', smooth: true, symbol: 'none', lineStyle: { color: '#47a9ff', width: 3 }, areaStyle: { color: 'rgba(71, 169, 255, .22)' }, data: snapshot?.hourlyCounts.map((item) => item.totalCount) ?? [] }],
  }), [snapshot]);

  const columns: ColumnsType<StatisticsScreenTopTransaction> = [
    { title: '重点交易', dataIndex: 'transactionName', width: 240, ellipsis: { showTitle: false }, render: (value: string) => <Tooltip title={value}>{value}</Tooltip> },
    { title: '调用量', dataIndex: 'totalCount', width: 100, align: 'right' },
    { title: '失败率', dataIndex: 'failureRate', width: 100, align: 'right', render: (value: number) => `${value}%` },
    { title: '平均响应', dataIndex: 'averageElapsedTime', width: 115, align: 'right', render: (value: number) => `${value} ms` },
  ];

  const query = () => void loadSnapshot(search);

  return <div ref={rootRef} className={`statistics-screen ${fullscreen ? 'is-fullscreen' : ''}`}>
    <header className='statistics-screen-header'>
      <div><div className='statistics-screen-kicker'>TRANSACTION OBSERVABILITY · MOCK SNAPSHOT</div><h1>交易监控大屏</h1></div>
      <div className='statistics-screen-tools'>
        <Select value={search.environmentId} options={environments.map((item) => ({ value: item.id, label: item.name }))} onChange={(value) => setSearch((current) => ({ ...current, environmentId: value }))} />
        <DatePicker value={search.statisticsDate} allowClear={false} onChange={(value) => value && setSearch((current) => ({ ...current, statisticsDate: value }))} />
        <Button className='statistics-screen-button' icon={<ReloadOutlined />} loading={refreshing} onClick={query}>刷新快照</Button>
        <Tooltip title={fullscreen ? '退出全屏' : '全屏展示'}><Button className='statistics-screen-button' icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} onClick={() => void toggleFullscreen()} /></Tooltip>
      </div>
    </header>
    <div className='statistics-screen-meta'>
      <span>统计日期：{snapshot?.statisticsDate ?? search.statisticsDate.format('YYYY-MM-DD')}</span>
      <span>最近更新时间：{snapshot ? dayjs(snapshot.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '--'}</span>
      <Tag color='gold'>模拟统计快照 · 每 60 秒重新获取 · 非实时监控</Tag>
    </div>
    {error ? <Alert className='statistics-screen-alert' type='error' showIcon message='监控快照加载失败' description={error} /> : null}
    <Spin spinning={loading || refreshing}>
      {!snapshot && !error ? <Empty className='statistics-screen-empty' description='暂无监控快照' /> : null}
      {snapshot ? <>
        <section className='statistics-screen-kpis'>
          {[
            ['总交易量', snapshot.totalCount, '笔', 'total'], ['成功 Y', snapshot.successCount, '笔', 'success'], ['查无数据 N', snapshot.noDataCount, '笔', 'nodata'], ['失败 E', snapshot.failureCount, '笔', 'failure'], ['失败率', snapshot.failureRate, '%', 'failure'], ['平均响应时间', snapshot.averageElapsedTime, 'ms', 'elapsed'], ['活跃调用系统', snapshot.activeCallerCount, '个', 'active'],
          ].map(([title, value, suffix, className]) => <div key={String(title)} className={`statistics-screen-kpi ${className}`}><Statistic title={title} value={value} suffix={suffix} /></div>)}
        </section>
        <section className='statistics-screen-grid statistics-screen-primary-grid'>
          <div className='statistics-screen-panel wide'><h2>最近七日交易趋势</h2><BaseChart option={trendOption} height={292} empty={!snapshot.sevenDayTrend.length} /></div>
          <div className='statistics-screen-panel'><h2>Y / N / E 占比</h2><BaseChart option={statusOption} height={292} empty={snapshot.totalCount === 0} /></div>
          <div className='statistics-screen-panel'><h2>交易调用量 TOP10</h2><BaseChart option={topOption} height={292} empty={!snapshot.topTransactions.length} /></div>
          <div className='statistics-screen-panel wide'><h2>近 24 小时调用分布</h2><BaseChart option={hourlyOption} height={292} empty={!snapshot.hourlyCounts.length} /></div>
        </section>
        <section className='statistics-screen-grid statistics-screen-bottom-grid'>
          <div className='statistics-screen-panel'><h2>重点交易运行情况</h2><Table className='statistics-screen-table' rowKey='transactionCode' columns={columns} dataSource={snapshot.topTransactions.slice(0, 5)} pagination={false} size='small' scroll={{ x: 560 }} /></div>
          <div className='statistics-screen-panel'><h2>统计指标提示</h2><div className='statistics-screen-alert-list'>{snapshot.alerts.map((item) => <Alert key={item.title} type={item.level === 'ERROR' ? 'error' : 'warning'} showIcon message={item.title} description={item.description} />)}</div></div>
        </section>
      </> : null}
    </Spin>
  </div>;
}
