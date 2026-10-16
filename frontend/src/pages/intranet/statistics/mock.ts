import dayjs from 'dayjs';
import type { DailyStatisticsPoint, DimensionOption, DimensionStatisticsFilter, DimensionStatisticsResult, DimensionStatisticsRow, HourlyStatisticsPoint, MonthlyStatisticsPoint, RankingStatisticsFilter, RankingStatisticsResult, RankingStatisticsRow, StatisticsDimensionType, StatisticsEnvironment, StatisticsFilter, StatisticsOverview, StatisticsScreenFilter, StatisticsScreenSnapshot, StatisticsTransaction, StatusStatistics } from './types';

const delay = (milliseconds: number) => new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));

const environments: StatisticsEnvironment[] = [
  { id: 'sandbox', name: '沙箱环境' },
  { id: 'integration', name: '集成环境' },
];

const transactions: StatisticsTransaction[] = [
  { transactionCode: 'DEMO_TRANSACTION_DETAIL_QUERY', transactionName: '交易明细查询' },
  { transactionCode: 'DEMO_RISK_PROFILE_QUERY', transactionName: '客户风险画像查询' },
  { transactionCode: 'TX-EMPTY', transactionName: '空结果演示交易' },
  { transactionCode: 'TX-ERROR', transactionName: '异常演示交易' },
];

const callers: DimensionOption[] = [
  { id: 'MOBILE_BANKING', label: 'MOBILE_BANKING - 手机银行' },
  { id: 'COUNTER', label: 'COUNTER - 柜面系统' },
  { id: 'RISK_PORTAL', label: 'RISK_PORTAL - 风险门户' },
];

const services: DimensionOption[] = [
  { id: 'CUSTOMER_PROFILE', label: 'CUSTOMER_PROFILE - 客户画像服务' },
  { id: 'TRANSACTION_DETAIL', label: 'TRANSACTION_DETAIL - 交易明细服务' },
  { id: 'RISK_PROFILE', label: 'RISK_PROFILE - 风险画像服务' },
];

function statusCounts(seed: number): StatusStatistics {
  const successCount = 42 + (seed * 13) % 65;
  const noDataCount = 4 + (seed * 5) % 16;
  const failureCount = 1 + (seed * 3) % 8;
  return { successCount, noDataCount, failureCount, totalCount: successCount + noDataCount + failureCount };
}

function averageElapsedTime(counts: StatusStatistics, seed: number) {
  const weightedElapsed = counts.successCount * (92 + seed % 20) + counts.noDataCount * (58 + seed % 12) + counts.failureCount * (210 + seed % 75);
  return Math.round(weightedElapsed / counts.totalCount);
}

function toDailyPoint(date: string, seed: number): DailyStatisticsPoint {
  const counts = statusCounts(seed);
  return { date, ...counts, averageElapsedTime: averageElapsedTime(counts, seed) };
}

function toDimensionRow(index: number, dimensionType: StatisticsDimensionType, seed: number): DimensionStatisticsRow {
  const counts = statusCounts(seed + index * 7);
  const transaction = transactions[index % 2];
  const caller = callers[index % callers.length];
  const service = services[index % services.length];
  const previousTotalCount = index === 3 ? 0 : Math.max(1, counts.totalCount - 12 - index * 4);
  const monthOnMonthRate = previousTotalCount ? Number((((counts.totalCount - previousTotalCount) / previousTotalCount) * 100).toFixed(1)) : undefined;
  return {
    id: `${dimensionType}-${index}`,
    ...counts,
    transactionCode: transaction.transactionCode,
    transactionName: transaction.transactionName,
    callerId: caller.id,
    callerName: caller.label.split(' - ')[1],
    serviceId: service.id,
    serviceName: service.label.split(' - ')[1],
    successRate: Number(((counts.successCount / counts.totalCount) * 100).toFixed(1)),
    previousTotalCount,
    monthOnMonthRate,
  };
}

function sum(points: Array<StatusStatistics & { averageElapsedTime?: number }>) {
  const counts = points.reduce<StatusStatistics>((total, point) => ({
    successCount: total.successCount + point.successCount,
    noDataCount: total.noDataCount + point.noDataCount,
    failureCount: total.failureCount + point.failureCount,
    totalCount: total.totalCount + point.totalCount,
  }), { successCount: 0, noDataCount: 0, failureCount: 0, totalCount: 0 });
  const elapsedTimeSampleCount = counts.totalCount;
  const averageElapsed = elapsedTimeSampleCount
    ? Math.round(points.reduce((total, point) => total + (point.averageElapsedTime ?? 0) * point.totalCount, 0) / elapsedTimeSampleCount)
    : 0;
  return { ...counts, averageElapsedTime: averageElapsed, elapsedTimeSampleCount };
}

export const statisticsMock = {
  async environments() {
    await delay(160);
    return environments;
  },
  async transactions() {
    await delay(160);
    return transactions;
  },
  async dimensionOptions() {
    await delay(140);
    return { callers, services };
  },
  async overview(filter: StatisticsFilter): Promise<StatisticsOverview> {
    await delay(720);
    if (filter.transactionCode === 'TX-ERROR') throw new Error('模拟统计服务暂不可用，请稍后重试');
    if (filter.transactionCode === 'TX-EMPTY') {
      return { successCount: 0, noDataCount: 0, failureCount: 0, totalCount: 0, averageElapsedTime: 0, elapsedTimeSampleCount: 0, dailyPoints: [], hourlyPoints: [], monthlyPoints: [] };
    }
    const start = dayjs(filter.startDate);
    const end = dayjs(filter.endDate);
    const days = Math.max(1, Math.min(end.diff(start, 'day') + 1, 31));
    const environmentSeed = filter.environmentId === 'integration' ? 19 : 7;
    const transactionSeed = filter.transactionCode === 'DEMO_RISK_PROFILE_QUERY' ? 11 : 0;
    const dailyPoints = Array.from({ length: days }, (_, index) => toDailyPoint(start.add(index, 'day').format('YYYY-MM-DD'), environmentSeed + transactionSeed + index));
    const hourlyPoints: HourlyStatisticsPoint[] = Array.from({ length: 24 }, (_, hour) => {
      const counts = statusCounts(environmentSeed + transactionSeed + hour + 31);
      return { hour: `${String(hour).padStart(2, '0')}:00`, ...counts, averageElapsedTime: averageElapsedTime(counts, environmentSeed + hour) };
    });
    const monthEnd = dayjs(filter.endDate).startOf('month');
    const monthlyPoints: MonthlyStatisticsPoint[] = Array.from({ length: 6 }, (_, index) => {
      const month = monthEnd.subtract(5 - index, 'month');
      const counts = statusCounts(environmentSeed + transactionSeed + index + 61);
      return { month: month.format('YYYY-MM'), isCurrentMonth: month.isSame(dayjs(), 'month'), ...counts };
    });
    return { ...sum(dailyPoints), dailyPoints, hourlyPoints, monthlyPoints };
  },
  async dimensions(filter: DimensionStatisticsFilter): Promise<DimensionStatisticsResult> {
    await delay(620);
    if (filter.transactionCode === 'TX-ERROR') throw new Error('模拟维度统计服务暂不可用，请稍后重试');
    if (filter.transactionCode === 'TX-EMPTY') return { rows: [], chartItems: [] };
    const environmentSeed = filter.environmentId === 'integration' ? 29 : 13;
    const rows = Array.from({ length: 9 }, (_, index) => toDimensionRow(index, filter.dimensionType, environmentSeed));
    const filtered = rows.filter((row) => (!filter.transactionCode || row.transactionCode === filter.transactionCode) && (!filter.callerId || row.callerId === filter.callerId) && (!filter.serviceId || row.serviceId === filter.serviceId));
    return {
      rows: filtered,
      chartItems: filtered.slice(0, 8).map((row) => ({
        id: row.id,
        label: filter.dimensionType === 'TRANSACTION_CALLER' ? `${row.transactionName} · ${row.callerName}` : filter.dimensionType === 'CALLER' ? row.callerName ?? row.id : filter.dimensionType === 'SERVICE' ? row.serviceName ?? row.id : row.transactionName ?? row.id,
        successCount: row.successCount,
        noDataCount: row.noDataCount,
        failureCount: row.failureCount,
        totalCount: row.totalCount,
      })),
    };
  },
  async rankings(filter: RankingStatisticsFilter): Promise<RankingStatisticsResult> {
    await delay(640);
    const environmentSeed = filter.environmentId === 'integration' ? 33 : 0;
    const monthSeed = dayjs(filter.month).month() * 9;
    const transactionNames = [
      ['DEMO_TRANSACTION_DETAIL_QUERY', '交易明细查询与结果整合服务'],
      ['DEMO_RISK_PROFILE_QUERY', '客户风险画像综合查询服务'],
      ['DEMO_ACCOUNT_POSITION_QUERY', '账户持仓及余额实时查询'],
      ['DEMO_PAYMENT_LIMIT_QUERY', '支付限额多维校验查询'],
      ['DEMO_CUSTOMER_TAG_QUERY', '客户标签与营销资格查询'],
      ['DEMO_LOAN_REPAYMENT_QUERY', '贷款还款计划及逾期信息查询'],
      ['DEMO_CARD_STATUS_QUERY', '银行卡状态与挂失记录查询'],
      ['DEMO_FX_RATE_QUERY', '外汇牌价与交叉汇率查询'],
      ['DEMO_PRODUCT_DETAIL_QUERY', '理财产品详情与风险等级查询'],
      ['DEMO_CHANNEL_AUTH_QUERY', '渠道授权与签约状态查询'],
      ['DEMO_BRANCH_SERVICE_QUERY', '网点服务能力与营业时间查询'],
    ];
    const callerNames = [
      ['MOBILE_BANKING', '手机银行渠道服务'],
      ['COUNTER', '柜面综合业务系统'],
      ['RISK_PORTAL', '风险运营门户'],
      ['CORPORATE_BANKING', '企业网银服务平台'],
      ['OPEN_API_GATEWAY', '开放银行网关'],
      ['CUSTOMER_CENTER', '客户服务中心'],
      ['WECHAT_MINI_PROGRAM', '微信小程序渠道'],
      ['BATCH_SETTLEMENT', '批量清算处理平台'],
      ['LOAN_PLATFORM', '信贷管理平台'],
      ['MARKETING_CENTER', '营销运营中心'],
      ['BRANCH_TERMINAL', '网点终端工作台'],
    ];
    const candidates = (filter.dimension === 'TRANSACTION' ? transactionNames : callerNames).map(([code, name], index) => {
      const totalCount = 980 - index * 71 + environmentSeed + monthSeed + (index % 4) * (monthSeed % 7);
      const previousTotalCount = index === 4 ? 0 : Math.max(1, totalCount - 160 + index * 23 - (monthSeed % 13));
      return {
        id: `${filter.dimension}-${code}`,
        rank: 0,
        dimensionCode: code,
        dimensionName: name,
        totalCount,
        previousTotalCount,
        growthRate: previousTotalCount ? Number((((totalCount - previousTotalCount) / previousTotalCount) * 100).toFixed(1)) : undefined,
        averageElapsedTime: 68 + (index * 37 + environmentSeed + monthSeed) % 285,
        elapsedTimeSampleCount: totalCount - (index % 3),
      } satisfies RankingStatisticsRow;
    });
    const value = (row: RankingStatisticsRow) => filter.metric === 'VOLUME' ? row.totalCount : filter.metric === 'AVG_ELAPSED_TIME' ? row.averageElapsedTime : row.growthRate;
    const ordered = [...candidates].sort((left, right) => {
      const leftValue = value(left);
      const rightValue = value(right);
      if (leftValue === undefined && rightValue === undefined) return left.dimensionCode.localeCompare(right.dimensionCode);
      if (leftValue === undefined) return 1;
      if (rightValue === undefined) return -1;
      return filter.order === 'ASC' ? leftValue - rightValue : rightValue - leftValue;
    }).slice(0, 10).map((row, index) => ({ ...row, rank: index + 1 }));
    const growthComparison = candidates.filter((row) => row.growthRate !== undefined).sort((left, right) => (right.growthRate ?? 0) - (left.growthRate ?? 0)).slice(0, 10).map((row, index) => ({ ...row, rank: index + 1 }));
    return { rows: ordered, growthComparison };
  },
  async screenSnapshot(filter: StatisticsScreenFilter): Promise<StatisticsScreenSnapshot> {
    await delay(520);
    const environmentSeed = filter.environmentId === 'integration' ? 17 : 5;
    const date = dayjs(filter.statisticsDate);
    const sevenDayTrend = Array.from({ length: 7 }, (_, index) => {
      const pointDate = date.subtract(6 - index, 'day');
      const counts = statusCounts(environmentSeed + pointDate.date() + index * 3);
      return { date: pointDate.format('MM-DD'), ...counts, averageElapsedTime: averageElapsedTime(counts, environmentSeed + index) };
    });
    const summary = sum(sevenDayTrend);
    const transactionNames = [
      ['DEMO_TRANSACTION_DETAIL_QUERY', '交易明细查询与结果整合服务'],
      ['DEMO_RISK_PROFILE_QUERY', '客户风险画像综合查询服务'],
      ['DEMO_ACCOUNT_POSITION_QUERY', '账户持仓及余额实时查询'],
      ['DEMO_PAYMENT_LIMIT_QUERY', '支付限额多维校验查询'],
      ['DEMO_LOAN_REPAYMENT_QUERY', '贷款还款计划及逾期信息查询'],
      ['DEMO_CARD_STATUS_QUERY', '银行卡状态与挂失记录查询'],
      ['DEMO_FX_RATE_QUERY', '外汇牌价与交叉汇率查询'],
      ['DEMO_PRODUCT_DETAIL_QUERY', '理财产品详情与风险等级查询'],
      ['DEMO_CHANNEL_AUTH_QUERY', '渠道授权与签约状态查询'],
      ['DEMO_BRANCH_SERVICE_QUERY', '网点服务能力与营业时间查询'],
    ];
    const topTransactions = transactionNames.map(([transactionCode, transactionName], index) => {
      const totalCount = 1260 - index * 87 + environmentSeed * 4;
      const failureCount = 3 + (index * 3 + environmentSeed) % 11;
      return {
        transactionCode,
        transactionName,
        totalCount,
        failureCount,
        failureRate: Number(((failureCount / totalCount) * 100).toFixed(2)),
        averageElapsedTime: 72 + (index * 29 + environmentSeed) % 230,
      };
    });
    const failureRate = Number(((summary.failureCount / summary.totalCount) * 100).toFixed(2));
    return {
      ...summary,
      statisticsDate: filter.statisticsDate,
      updatedAt: new Date().toISOString(),
      failureRate,
      activeCallerCount: filter.environmentId === 'integration' ? 14 : 11,
      sevenDayTrend,
      hourlyCounts: Array.from({ length: 24 }, (_, hour) => ({ hour: `${String(hour).padStart(2, '0')}:00`, totalCount: 38 + (hour * 17 + environmentSeed * 5) % 138 })),
      topTransactions,
      alerts: [
        { level: 'WARNING', title: '失败率状态', description: `当前统计周期失败率为 ${failureRate}%（仅展示统计快照，不代表实时告警）。` },
        { level: 'WARNING', title: '响应时间状态', description: `平均响应时间为 ${summary.averageElapsedTime} ms，未配置阈值或自动处置规则。` },
      ],
    };
  },
};
