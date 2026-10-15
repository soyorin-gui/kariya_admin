import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Button, DatePicker, Input, Select, Space, Spin, Tag, TimePicker } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { SmartTable } from '../../../../components/SmartTable';
import { Permission } from '../../../../permission/Permission';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { getTransactions } from '../../transaction/api';
import type { TransactionListItem } from '../../transaction/types';
import { getFlowEnvironments, getFlowFilterOptions, queryTransactionFlows } from './api';
import type { FlowEnvironment, TransactionFlowRecord } from './types';

type Search = {
  environmentId?: string;
  queryDate: Dayjs;
  callerId: string;
  serviceAddress: string;
  transactionCode?: string;
  startTime?: Dayjs;
  endTime?: Dayjs;
  responseStatus?: string;
  responseCode?: string;
  globalFlowId: string;
};

const STATUS_OPTIONS = [
  { label: 'Y', value: 'Y' },
  { label: 'N', value: 'N' },
  { label: 'E', value: 'E' },
];

const statusColor: Record<string, string> = { Y: 'success', N: 'warning', E: 'error' };

function disabledQueryDate(current: Dayjs): boolean {
  const today = dayjs().startOf('day');
  return current.startOf('day').isAfter(today) || current.startOf('day').isBefore(today.subtract(39, 'day'));
}

function dateTime(date: Dayjs, time?: Dayjs): string | undefined {
  if (!time) return undefined;
  return `${date.format('YYYY-MM-DD')} ${time.format('HH:mm:ss')}`;
}

export default function TransactionFlowPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [environments, setEnvironments] = useState<FlowEnvironment[]>([]);
  const [responseCodes, setResponseCodes] = useState<string[]>([]);
  const [transactions, setTransactions] = useState<TransactionListItem[]>([]);
  const [queriedEnvironmentId, setQueriedEnvironmentId] = useState<string>();

  const loadTransactions = async (keyword?: string) => {
    const data = await getTransactions({ pageNum: 1, pageSize: 100, keyword });
    setTransactions(data.records);
  };

  const loadOptions = async (environmentId?: string) => {
    if (!environmentId) {
      setResponseCodes([]);
      return;
    }
    try {
      const options = await getFlowFilterOptions(environmentId);
      setResponseCodes(options.responseCodes);
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载流水筛选项'));
    }
  };

  useEffect(() => {
    void Promise.all([getFlowEnvironments(), loadTransactions()])
      .then(([environmentList]) => {
        setEnvironments(environmentList);
        return loadOptions(environmentList[0]?.id);
      })
      .catch((error) => message.error(getApiErrorMessage(error, '无法初始化流水查询页面')))
      .finally(() => setLoading(false));
  }, []);

  const columns: ColumnsType<TransactionFlowRecord> = [
    { title: '交易码', dataIndex: 'transactionCode', width: 110, fixed: 'left' },
    { title: '调用方 ID', dataIndex: 'callerId', width: 140 },
    { title: 'IP-服务地址', dataIndex: 'serviceAddress', width: 230, ellipsis: true },
    { title: '开始时间', dataIndex: 'startTime', width: 170 },
    { title: '结束时间', dataIndex: 'endTime', width: 170 },
    { title: '耗时', dataIndex: 'elapsedTime', width: 100, align: 'right', render: (value) => `${value} ms` },
    { title: '响应状态', dataIndex: 'responseStatus', width: 100, align: 'center', render: (value) => <Tag color={statusColor[value]}>{value}</Tag> },
    { title: '响应码', dataIndex: 'responseCode', width: 150 },
    { title: '查询日期', dataIndex: 'queryDate', width: 120 },
    { title: '全局流水号', dataIndex: 'globalFlowId', width: 250 },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Permission code='intranet:operation:log:list'>
          <a
            onClick={() =>
              void navigate({
                pathname: '/intranet/operation/logs',
                search: new URLSearchParams({ environmentId: queriedEnvironmentId ?? '', queryDate: record.queryDate, globalFlowId: record.globalFlowId }).toString(),
              })
            }
          >
            日志追踪
          </a>
        </Permission>
      ),
    },
  ];

  if (loading) return <Spin fullscreen tip='正在加载流水查询配置' />;

  const initialEnvironment = environments[0]?.id;
  return (
    <div className='page-fill operation-flow-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>TRANSACTION OPERATION</div>
          <h1 className='page-title'>交易流水查询</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<TransactionFlowRecord, Search>
          rowKey='globalFlowId'
          columns={columns}
          initialSearch={{
            environmentId: initialEnvironment,
            queryDate: dayjs(),
            callerId: '',
            serviceAddress: '',
            globalFlowId: '',
          }}
          request={async ({ page, pageSize, search }) => {
            if (!search.environmentId || !search.queryDate) return { list: [], total: 0 };
            setQueriedEnvironmentId(search.environmentId);
            const data = await queryTransactionFlows({
              environmentId: search.environmentId,
              queryDate: search.queryDate.format('YYYY-MM-DD'),
              callerId: search.callerId || undefined,
              serviceAddress: search.serviceAddress || undefined,
              transactionCode: search.transactionCode,
              startTime: dateTime(search.queryDate, search.startTime),
              endTime: dateTime(search.queryDate, search.endTime),
              responseStatus: search.responseStatus,
              responseCode: search.responseCode,
              globalFlowId: search.globalFlowId || undefined,
              pageNum: page,
              pageSize,
            });
            return { list: data.records, total: data.total };
          }}
          onRequestError={(error) => message.error(getApiErrorMessage(error, '无法查询交易流水'))}
          toolbarClassName='table-toolbar'
          searchActions={{ collapsible: true, defaultExpanded: false }}
          searchRender={({ search, setSearch, submit, reset }) => (
            <div className='smart-table-search-form'>
              <div className='smart-table-search-fields'>
                <label className='smart-table-search-field'>
                  <span>运行环境</span>
                  <Select
                    value={search.environmentId}
                    options={environments.map((item) => ({ label: item.name, value: item.id }))}
                    placeholder='请选择运行环境'
                    style={{ width: 180 }}
                    onChange={(value) => {
                      setSearch({ ...search, environmentId: value, responseCode: undefined });
                      void loadOptions(value);
                    }}
                  />
                </label>
                <label className='smart-table-search-field'>
                  <span>查询日期</span>
                  <DatePicker value={search.queryDate} allowClear={false} disabledDate={disabledQueryDate} onChange={(value) => value && setSearch({ ...search, queryDate: value })} />
                </label>
                <label className='smart-table-search-field'>
                  <span>全局流水号</span>
                  <Input value={search.globalFlowId} allowClear placeholder='精确匹配' style={{ width: 230 }} onChange={(event) => setSearch({ ...search, globalFlowId: event.target.value })} onPressEnter={() => submit()} />
                </label>
                <label className='smart-table-search-field'>
                  <span>交易码</span>
                  <Select
                    value={search.transactionCode}
                    allowClear
                    showSearch
                    filterOption={false}
                    placeholder='请选择交易'
                    style={{ width: 240 }}
                    options={transactions.map((item) => ({ label: `${item.transactionCode} - ${item.transactionName}`, value: item.transactionCode }))}
                    onSearch={(keyword) => void loadTransactions(keyword).catch((error) => message.error(getApiErrorMessage(error, '无法搜索交易资产')))}
                    onChange={(value) => setSearch({ ...search, transactionCode: value })}
                  />
                </label>
                <label className='smart-table-search-field'>
                  <span>查询时间</span>
                  <Space.Compact>
                    <TimePicker value={search.startTime} format='HH:mm:ss' placeholder='开始时间' onChange={(value) => setSearch({ ...search, startTime: value ?? undefined })} />
                    <TimePicker value={search.endTime} format='HH:mm:ss' placeholder='结束时间' onChange={(value) => setSearch({ ...search, endTime: value ?? undefined })} />
                  </Space.Compact>
                </label>
                <label className='smart-table-search-field'>
                  <span>调用方 ID</span>
                  <Input value={search.callerId} allowClear placeholder='模糊匹配' style={{ width: 170 }} onChange={(event) => setSearch({ ...search, callerId: event.target.value })} onPressEnter={() => submit()} />
                </label>
                <label className='smart-table-search-field'>
                  <span>IP-服务地址</span>
                  <Input value={search.serviceAddress} allowClear placeholder='请输入 IP-服务地址' style={{ width: 210 }} onChange={(event) => setSearch({ ...search, serviceAddress: event.target.value })} onPressEnter={() => submit()} />
                </label>
                <label className='smart-table-search-field'>
                  <span>响应状态</span>
                  <Select value={search.responseStatus} allowClear options={STATUS_OPTIONS} placeholder='请选择' style={{ width: 110 }} onChange={(value) => setSearch({ ...search, responseStatus: value })} />
                </label>
                <label className='smart-table-search-field'>
                  <span>响应码</span>
                  <Select value={search.responseCode} allowClear options={responseCodes.map((value) => ({ label: value, value }))} placeholder='请选择' style={{ width: 170 }} onChange={(value) => setSearch({ ...search, responseCode: value })} />
                </label>
              </div>
              <Space className='smart-table-search-actions' wrap>
                <Button type='primary' icon={<SearchOutlined />} onClick={() => submit()}>
                  查询
                </Button>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => {
                    reset();
                    void loadOptions(initialEnvironment);
                  }}
                >
                  重置
                </Button>
              </Space>
            </div>
          )}
          pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条记录` }}
          scroll={{ x: 1650 }}
        />
      </div>
    </div>
  );
}
