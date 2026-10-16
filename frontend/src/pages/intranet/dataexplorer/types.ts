export type DataSourceType = 'ELASTICSEARCH' | 'HBASE' | 'REDIS';

export interface DataExplorerEnvironment {
  id: string;
  name: string;
  connectionLabel: string;
}

export interface DataExplorerResource {
  id: string;
  sourceType: DataSourceType;
  name: string;
  group?: string;
  description: string;
  detail?: string;
}

export interface QueryTemplate {
  id: string;
  sourceType: DataSourceType;
  name: string;
  description: string;
  queryText: string;
}

export interface DataExplorerQuery {
  sourceType: DataSourceType;
  environmentId: string;
  resourceId?: string;
  templateId?: string;
  queryText: string;
}

export interface QueryColumn {
  key: string;
  title: string;
  width?: number;
}

export interface QueryRecord {
  id: string;
  values: Record<string, unknown>;
  raw: unknown;
}

export interface DataExplorerQueryResult {
  sourceType: DataSourceType;
  columns: QueryColumn[];
  records: QueryRecord[];
  total?: number;
  returnedCount: number;
  elapsedTimeMs: number;
  rawResult: string;
  warnings: string[];
}
