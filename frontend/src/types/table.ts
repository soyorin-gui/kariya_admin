import type { Key } from 'react';

export interface TableSorter {
  field?: string;
  order?: 'asc' | 'desc';
}

export interface TableRequestParams<Search extends Record<string, unknown> = Record<string, unknown>> {
  page: number;
  pageSize: number;
  sorter?: TableSorter;
  filters?: Record<string, unknown>;
  search: Search;
}

export interface TableRequestResult<T> {
  list: T[];
  total: number;
}

export type TableRequest<T, Search extends Record<string, unknown> = Record<string, unknown>> = (params: TableRequestParams<Search>) => Promise<TableRequestResult<T>>;

export interface SmartTableReloadOptions {
  /** 指定刷新后的页码；省略时保留当前页。 */
  page?: number;
  /** 回到第一页，优先级低于 page。 */
  resetPage?: boolean;
}

export interface SmartTableRef<Search extends Record<string, unknown> = Record<string, unknown>> {
  reload(options?: SmartTableReloadOptions): void;
  reset(): void;
  getQueryParams(): TableRequestParams<Search>;
}

export interface SmartTableSelection<T> {
  selectedRowKeys: Key[];
  selectedRows: T[];
}
