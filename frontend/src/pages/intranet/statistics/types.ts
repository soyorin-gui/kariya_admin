export type TransactionStatus = 'Y' | 'N' | 'E';

export interface StatisticsEnvironment {
  id: string;
  name: string;
}

export interface StatisticsTransaction {
  transactionCode: string;
  transactionName: string;
}

export interface StatisticsFilter {
  environmentId: string;
  startDate: string;
  endDate: string;
  hourDate: string;
  transactionCode?: string;
}

export interface StatusStatistics {
  successCount: number;
  noDataCount: number;
  failureCount: number;
  totalCount: number;
}

export interface DailyStatisticsPoint extends StatusStatistics {
  date: string;
  averageElapsedTime: number;
}

export interface HourlyStatisticsPoint extends StatusStatistics {
  hour: string;
  averageElapsedTime: number;
}

export interface MonthlyStatisticsPoint extends StatusStatistics {
  month: string;
  isCurrentMonth: boolean;
}

export interface StatisticsOverview extends StatusStatistics {
  averageElapsedTime: number;
  elapsedTimeSampleCount: number;
  dailyPoints: DailyStatisticsPoint[];
  hourlyPoints: HourlyStatisticsPoint[];
  monthlyPoints: MonthlyStatisticsPoint[];
}

export type StatisticsDimensionType = 'TRANSACTION' | 'CALLER' | 'TRANSACTION_CALLER' | 'SERVICE';

export interface DimensionOption {
  id: string;
  label: string;
}

export interface DimensionStatisticsFilter {
  environmentId: string;
  startMonth: string;
  endMonth: string;
  dimensionType: StatisticsDimensionType;
  transactionCode?: string;
  callerId?: string;
  serviceId?: string;
}

export interface DimensionStatisticsRow extends StatusStatistics {
  id: string;
  transactionCode?: string;
  transactionName?: string;
  callerId?: string;
  callerName?: string;
  serviceId?: string;
  serviceName?: string;
  successRate: number;
  previousTotalCount: number;
  monthOnMonthRate?: number;
}

export interface DimensionStatisticsResult {
  rows: DimensionStatisticsRow[];
  chartItems: Array<Pick<DimensionStatisticsRow, 'id' | 'successCount' | 'noDataCount' | 'failureCount' | 'totalCount'> & { label: string }>;
}

export type StatisticsRankingMetric = 'VOLUME' | 'GROWTH' | 'AVG_ELAPSED_TIME';
export type StatisticsRankingDimension = 'TRANSACTION' | 'CALLER';
export type StatisticsRankingOrder = 'ASC' | 'DESC';

export interface RankingStatisticsFilter {
  environmentId: string;
  month: string;
  metric: StatisticsRankingMetric;
  dimension: StatisticsRankingDimension;
  order: StatisticsRankingOrder;
}

export interface RankingStatisticsRow {
  id: string;
  rank: number;
  dimensionCode: string;
  dimensionName: string;
  totalCount: number;
  previousTotalCount: number;
  growthRate?: number;
  averageElapsedTime: number;
  elapsedTimeSampleCount: number;
}

export interface RankingStatisticsResult {
  rows: RankingStatisticsRow[];
  growthComparison: RankingStatisticsRow[];
}

export interface StatisticsScreenFilter {
  environmentId: string;
  statisticsDate: string;
}

export interface StatisticsScreenTrendPoint extends StatusStatistics {
  date: string;
  averageElapsedTime: number;
}

export interface StatisticsScreenTopTransaction {
  transactionCode: string;
  transactionName: string;
  totalCount: number;
  failureCount: number;
  failureRate: number;
  averageElapsedTime: number;
}

export interface StatisticsScreenAlert {
  level: 'WARNING' | 'ERROR';
  title: string;
  description: string;
}

export interface StatisticsScreenSnapshot extends StatusStatistics {
  statisticsDate: string;
  updatedAt: string;
  failureRate: number;
  averageElapsedTime: number;
  activeCallerCount: number;
  sevenDayTrend: StatisticsScreenTrendPoint[];
  hourlyCounts: Array<{ hour: string; totalCount: number }>;
  topTransactions: StatisticsScreenTopTransaction[];
  alerts: StatisticsScreenAlert[];
}
