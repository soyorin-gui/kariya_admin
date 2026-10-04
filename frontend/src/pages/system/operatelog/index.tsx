import { useRef, useState } from 'react';
import { App, Button, DatePicker, Input, Popconfirm, Select, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteOperationLogs, getOperationLogs } from '../../../api/operationLog';
import { Permission } from '../../../permission/Permission';
import type { LogResult, OperationLog } from '../../../types/log';
import { getApiErrorMessage } from '../../../utils/apiError';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import './index.css';

const RESULT_META: Record<string, { text: string; color: string }> = {
  SUCCESS: { text: '成功', color: 'success' },
  FAILURE: { text: '失败', color: 'error' },
};
const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
];
const TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

type LogFilters = {
  keyword: string;
  module: string;
  result?: LogResult;
  range: [Dayjs, Dayjs] | null;
};

export default function OperationLogPage() {
  const { message } = App.useApp();
  const tableRef = useRef<SmartTableRef<LogFilters>>(null);
  const currentRecordCount = useRef(0);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  const remove = async () => {
    try {
      const response = await deleteOperationLogs(selectedIds);
      message.success(response.message || '删除成功');
      const page = tableRef.current?.getQueryParams().page ?? 1;
      setSelectedIds([]);
      tableRef.current?.reload({ page: currentRecordCount.current === selectedIds.length && page > 1 ? page - 1 : page });
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败，请稍后重试'));
    }
  };

  const columns: ColumnsType<OperationLog> = [
    { title: '操作人', dataIndex: 'username', width: 130, render: (value) => <span className='log-strong'>{value || '-'}</span> },
    { title: '模块', dataIndex: 'module', width: 120, render: (value) => <Tag>{value}</Tag> },
    { title: '操作', dataIndex: 'action', width: 140 },
    {
      title: '操作对象', width: 190, render: (_, row) => <div>
        <div>{row.targetName || row.targetId || '-'}</div>
        {row.targetType && <small className='log-mono'>{row.targetType}{row.targetId && row.targetName ? ` · ${row.targetId}` : ''}</small>}
      </div>,
    },
    { title: '结果', dataIndex: 'result', width: 95, render: (value: string) => <Tag color={RESULT_META[value]?.color}>{RESULT_META[value]?.text ?? value}</Tag> },
    { title: '失败原因', dataIndex: 'errorMessage', width: 220, ellipsis: { showTitle: true }, render: (value) => value || '-' },
    { title: '请求', width: 250, render: (_, row) => <div><span className='log-mono'>{row.requestMethod || '-'} {row.requestUri || '-'}</span>{row.requestId && <div><small className='log-mono'>RID: {row.requestId}</small></div>}</div> },
    { title: '请求 IP', dataIndex: 'requestIp', width: 150, render: (value) => <span className='log-mono'>{value || '-'}</span> },
    { title: '耗时', dataIndex: 'durationMs', width: 100, render: (value: number | null) => <span className='log-mono'>{value === null || value === undefined ? '-' : `${value} ms`}</span> },
    { title: '操作时间', dataIndex: 'createdTime', width: 180, render: (value) => <span className='log-time'>{value?.replace('T', ' ') || '-'}</span> },
  ];

  return (
    <div className='log-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>操作日志</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<OperationLog, LogFilters>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '', module: '', range: null }}
          request={async ({ page, pageSize, search }) => {
            const data = await getOperationLogs({ pageNum: page, pageSize, keyword: search.keyword.trim() || undefined, module: search.module.trim() || undefined, result: search.result, beginTime: search.range?.[0]?.format(TIME_FORMAT), endTime: search.range?.[1]?.format(TIME_FORMAT) });
            return { list: data.records, total: data.total };
          }}
          onRequestError={(error) => message.error(getApiErrorMessage(error, '无法获取操作日志'))}
          onDataLoaded={({ list }) => { currentRecordCount.current = list.length; setSelectedIds([]); }}
          toolbarClassName='table-toolbar log-toolbar'
          searchRender={({ search, setSearch, submit }) => <Space wrap>
            <Input value={search.keyword} onChange={(event) => setSearch({ ...search, keyword: event.target.value })} onPressEnter={() => submit()} prefix={<SearchOutlined />} placeholder='搜索操作人、对象或请求 ID' allowClear />
            <Input value={search.module} onChange={(event) => setSearch({ ...search, module: event.target.value })} onPressEnter={() => submit()} placeholder='模块，例如：用户管理' allowClear style={{ width: 190 }} />
            <Select value={search.result} onChange={(value) => setSearch({ ...search, result: value })} options={RESULT_OPTIONS} placeholder='操作结果' allowClear style={{ width: 130 }} />
            <DatePicker.RangePicker showTime value={search.range} onChange={(value) => setSearch({ ...search, range: value as [Dayjs, Dayjs] | null })} placeholder={['开始时间', '结束时间']} />
            <Button type='primary' icon={<SearchOutlined />} onClick={() => submit()}>查询</Button>
          </Space>}
          toolbarRender={({ reload }) => <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => reload()}>刷新</Button>
            <Permission code='system:operatelog:delete'><Popconfirm title='确定删除选中的操作日志？' description='日志删除后不可恢复，且删除动作本身会被记入操作日志。' okText='删除' cancelText='取消' disabled={!selectedIds.length} onConfirm={() => void remove()}><Button danger icon={<DeleteOutlined />} disabled={!selectedIds.length}>删除{selectedIds.length ? `（${selectedIds.length}）` : ''}</Button></Popconfirm></Permission>
          </Space>}
          rowSelection={{ selectedRowKeys: selectedIds, onChange: (keys) => setSelectedIds(keys.map(Number)) }}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (value) => `共 ${value} 条记录` }}
          scroll={{ x: 1650 }}
        />
      </div>
    </div>
  );
}
