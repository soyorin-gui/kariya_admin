import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState, type ReactElement, type ReactNode, type Ref } from 'react';
import { Button, DatePicker, Input, Select, Space, Table } from 'antd';
import type { TablePaginationConfig, TableProps } from 'antd';
import type { FilterValue, SorterResult, TableCurrentDataSource } from 'antd/es/table/interface';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { SmartTableRef, TableRequest, TableRequestParams, TableSorter } from '../../types/table';

export interface SmartTableActions<Search extends Record<string, unknown>> {
  search: Search;
  setSearch: (next: Search | ((current: Search) => Search)) => void;
  submit: (next?: Search) => void;
  reset: () => void;
  reload: SmartTableRef<Search>['reload'];
  exportData: () => void;
  getQueryParams: () => TableRequestParams<Search>;
}

export type SearchFieldType = 'input' | 'select' | 'date' | 'dateRange';

export interface SearchField<Search extends Record<string, unknown>> {
  name: Extract<keyof Search, string>;
  label?: string;
  type: SearchFieldType;
  placeholder?: string;
  options?: Array<{ label: string; value: string | number }>;
}

type AntTableChange<T> = (pagination: TablePaginationConfig, filters: Record<string, FilterValue | null>, sorter: SorterResult<T> | SorterResult<T>[], extra: TableCurrentDataSource<T>) => void;

export interface SmartTableProps<T extends object, Search extends Record<string, unknown> = Record<string, unknown>> extends Omit<TableProps<T>, 'dataSource' | 'loading' | 'pagination' | 'onChange'> {
  request: TableRequest<T, Search>;
  initialSearch?: Search;
  pagination?: false | Omit<TablePaginationConfig, 'current' | 'total' | 'onChange'>;
  searchConfig?: Array<SearchField<Search>>;
  searchRender?: (actions: SmartTableActions<Search>) => ReactNode;
  toolbarRender?: (actions: SmartTableActions<Search>) => ReactNode;
  onExport?: (params: TableRequestParams<Search>) => void | Promise<void>;
  onRequestError?: (error: unknown) => void;
  onDataLoaded?: (result: { list: T[]; total: number }, params: TableRequestParams<Search>) => void;
  onTableChange?: AntTableChange<T>;
  toolbarClassName?: string;
  searchClassName?: string;
  actionsClassName?: string;
  tableClassName?: string;
}

function normalizeSorter<T>(value: SorterResult<T> | SorterResult<T>[]): TableSorter | undefined {
  const sorter = Array.isArray(value) ? value.find((item) => item.order) : value;
  if (!sorter?.order) return undefined;
  const rawField = sorter.field ?? sorter.columnKey;
  const field = Array.isArray(rawField) ? rawField.join('.') : rawField === undefined ? undefined : String(rawField);
  return { field, order: sorter.order === 'ascend' ? 'asc' : 'desc' };
}

function SmartTableInner<T extends object, Search extends Record<string, unknown>>(
  {
    request,
    initialSearch,
    pagination = {},
    searchConfig,
    searchRender,
    toolbarRender,
    onExport,
    onRequestError,
    onDataLoaded,
    onTableChange,
    toolbarClassName,
    searchClassName,
    actionsClassName,
    tableClassName,
    ...tableProps
  }: SmartTableProps<T, Search>,
  ref: Ref<SmartTableRef<Search>>,
) {
  const initialSearchRef = useRef<Search>({ ...(initialSearch ?? ({} as Search)) });
  const requestRef = useRef(request);
  const onRequestErrorRef = useRef(onRequestError);
  const onDataLoadedRef = useRef(onDataLoaded);
  requestRef.current = request;
  onRequestErrorRef.current = onRequestError;
  onDataLoadedRef.current = onDataLoaded;

  const initialPageSize = pagination === false ? 10 : Number(pagination.pageSize ?? pagination.defaultPageSize ?? 10);
  const [records, setRecords] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [search, setSearch] = useState<Search>(initialSearchRef.current);
  const [submittedSearch, setSubmittedSearch] = useState<Search>(initialSearchRef.current);
  const [sorter, setSorter] = useState<TableSorter>();
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [reloadToken, setReloadToken] = useState(0);
  const [tableStateVersion, setTableStateVersion] = useState(0);
  const requestSequence = useRef(0);

  const queryParams = useMemo<TableRequestParams<Search>>(() => ({ page, pageSize, sorter, filters, search: submittedSearch }), [filters, page, pageSize, sorter, submittedSearch]);
  const queryParamsRef = useRef(queryParams);
  queryParamsRef.current = queryParams;

  useEffect(() => {
    const sequence = ++requestSequence.current;
    let active = true;
    const run = async () => {
      try {
        setLoading(true);
        const result = await requestRef.current(queryParams);
        if (!active || sequence !== requestSequence.current) return;
        setRecords(result.list);
        setTotal(result.total);
        onDataLoadedRef.current?.(result, queryParams);
      } catch (error) {
        if (active && sequence === requestSequence.current) onRequestErrorRef.current?.(error);
      } finally {
        if (active && sequence === requestSequence.current) setLoading(false);
      }
    };
    void run();
    return () => {
      active = false;
    };
  }, [queryParams, reloadToken]);

  const reload = useCallback<SmartTableRef<Search>['reload']>((options) => {
    const nextPage = options?.page ?? (options?.resetPage ? 1 : queryParamsRef.current.page);
    if (nextPage !== queryParamsRef.current.page) setPage(nextPage);
    else setReloadToken((value) => value + 1);
  }, []);

  const submit = useCallback(
    (next?: Search) => {
      const value = next ?? search;
      setSearch(value);
      setSubmittedSearch({ ...value });
      setPage(1);
    },
    [search],
  );

  const reset = useCallback(() => {
    const value = { ...initialSearchRef.current };
    setSearch(value);
    setSubmittedSearch(value);
    setPage(1);
    setSorter(undefined);
    setFilters({});
    // Ant Design Table 会在内部保存表头筛选和排序状态。仅清空请求参数会造成
    // “数据已经重置，但漏斗/排序图标仍处于选中状态”，通过 key 同步复位内部状态。
    setTableStateVersion((current) => current + 1);
    setReloadToken((current) => current + 1);
  }, []);

  const getQueryParams = useCallback(() => queryParamsRef.current, []);
  const exportData = useCallback(() => {
    void onExport?.(queryParamsRef.current);
  }, [onExport]);

  useImperativeHandle(ref, () => ({ reload, reset, getQueryParams }), [getQueryParams, reload, reset]);

  const actions = useMemo<SmartTableActions<Search>>(() => ({ search, setSearch, submit, reset, reload, exportData, getQueryParams }), [exportData, getQueryParams, reload, reset, search, submit]);

  const configuredSearch = searchConfig?.length ? (
    <Space wrap>
      {searchConfig.map((field) => {
        const value = search[field.name];
        const update = (next: unknown) => setSearch((current) => ({ ...current, [field.name]: next }));
        if (field.type === 'select') {
          return <Select key={field.name} value={value as string | number | undefined} options={field.options} allowClear placeholder={field.placeholder ?? field.label} onChange={update} />;
        }
        if (field.type === 'date') {
          return <DatePicker key={field.name} value={value as never} placeholder={field.placeholder ?? field.label} onChange={update} />;
        }
        if (field.type === 'dateRange') {
          return <DatePicker.RangePicker key={field.name} value={value as never} onChange={update} />;
        }
        return (
          <Input
            key={field.name}
            value={value as string | number | undefined}
            allowClear
            placeholder={field.placeholder ?? field.label}
            onChange={(event) => update(event.target.value)}
            onPressEnter={() => submit()}
          />
        );
      })}
      <Button type='primary' icon={<SearchOutlined />} onClick={() => submit()}>
        查询
      </Button>
      <Button icon={<ReloadOutlined />} onClick={reset}>
        重置
      </Button>
    </Space>
  ) : null;

  const handleChange: AntTableChange<T> = (nextPagination, nextFilters, nextSorter, extra) => {
    if (pagination !== false) {
      setPage(nextPagination.current ?? 1);
      setPageSize(nextPagination.pageSize ?? pageSize);
    }
    setFilters(nextFilters);
    setSorter(normalizeSorter(nextSorter));
    onTableChange?.(nextPagination, nextFilters, nextSorter, extra);
  };

  const searchContent = searchRender?.(actions) ?? configuredSearch;
  const toolbarContent = toolbarRender?.(actions);
  const tablePagination: TableProps<T>['pagination'] = pagination === false ? false : { ...pagination, current: page, pageSize, total };

  return (
    <>
      {(searchContent || toolbarContent) && (
        <div className={toolbarClassName}>
          {searchClassName && searchContent ? <div className={searchClassName}>{searchContent}</div> : searchContent}
          {actionsClassName && toolbarContent ? <div className={actionsClassName}>{toolbarContent}</div> : toolbarContent}
        </div>
      )}
      <Table<T> key={tableStateVersion} {...tableProps} className={tableClassName} dataSource={records} loading={loading} pagination={tablePagination} onChange={handleChange} />
    </>
  );
}

export const SmartTable = forwardRef(SmartTableInner) as <T extends object, Search extends Record<string, unknown> = Record<string, unknown>>(
  props: SmartTableProps<T, Search> & { ref?: Ref<SmartTableRef<Search>> },
) => ReactElement;
