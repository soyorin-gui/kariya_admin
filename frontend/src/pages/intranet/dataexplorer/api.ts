import { dataExplorerMock } from './mock';
import type { DataExplorerQuery, DataExplorerQueryResult, DataExplorerResource, DataExplorerEnvironment, DataSourceType, QueryTemplate } from './types';

// D1 外网阶段只使用集中前端 Mock；内网接入时仅在本文件替换为 request 调用，页面契约保持不变。
export const getDataExplorerEnvironments = (): Promise<DataExplorerEnvironment[]> => dataExplorerMock.environments();
export const getDataExplorerResources = (sourceType: DataSourceType, keyword?: string): Promise<DataExplorerResource[]> => dataExplorerMock.resources(sourceType, keyword);
export const getDataExplorerTemplates = (sourceType: DataSourceType): Promise<QueryTemplate[]> => dataExplorerMock.templates(sourceType);
export const executeDataExplorerQuery = (query: DataExplorerQuery): Promise<DataExplorerQueryResult> => dataExplorerMock.execute(query);
