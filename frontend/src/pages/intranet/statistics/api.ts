import { statisticsMock } from './mock';
import type { DimensionStatisticsFilter, DimensionStatisticsResult, RankingStatisticsFilter, RankingStatisticsResult, StatisticsEnvironment, StatisticsFilter, StatisticsOverview, StatisticsScreenFilter, StatisticsScreenSnapshot, StatisticsTransaction } from './types';

// S1 外网阶段只使用集中前端 Mock；内网接入时仅替换本文件为真实 request 调用。
export const getStatisticsEnvironments = (): Promise<StatisticsEnvironment[]> => statisticsMock.environments();
export const getStatisticsTransactions = (): Promise<StatisticsTransaction[]> => statisticsMock.transactions();
export const getStatisticsOverview = (filter: StatisticsFilter): Promise<StatisticsOverview> => statisticsMock.overview(filter);
export const getStatisticsDimensionOptions = () => statisticsMock.dimensionOptions();
export const getDimensionStatistics = (filter: DimensionStatisticsFilter): Promise<DimensionStatisticsResult> => statisticsMock.dimensions(filter);
export const getRankingStatistics = (filter: RankingStatisticsFilter): Promise<RankingStatisticsResult> => statisticsMock.rankings(filter);
export const getStatisticsScreenSnapshot = (filter: StatisticsScreenFilter): Promise<StatisticsScreenSnapshot> => statisticsMock.screenSnapshot(filter);
