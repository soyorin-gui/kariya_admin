import { useEffect, useState } from 'react';
import { App, Button, DatePicker, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteOperationLogs, getOperationLogs } from '../../../api/operationLog';
import { Permission } from '../../../permission/Permission';
import type { LogResult, OperationLog } from '../../../types/log';
import { getApiErrorMessage } from '../../../utils/apiError';
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
const PAGE_SIZE = 10;

interface LogFilters {
  keyword: string;
  module: string;
  result?: LogResult;
  range: [Dayjs, Dayjs] | null;
}

export default function OperationLogPage() {
  const { message } = App.useApp();
  const [records, setRecords] = useState<OperationLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [draft, setDraft] = useState<LogFilters>({ keyword: '', module: '', range: null });
  const [query, setQuery] = useState<LogFilters>(draft);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;
    const run = async () => {
      try {
        setLoading(true);
        const data = await getOperationLogs({
          pageNum: page,
          pageSize: PAGE_SIZE,
          keyword: query.keyword.trim() || undefined,
          module: query.module.trim() || undefined,
          result: query.result,
          beginTime: query.range?.[0]?.format(TIME_FORMAT),
          endTime: query.range?.[1]?.format(TIME_FORMAT),
        });
        if (!active) return;
        setRecords(data.records);
        setTotal(data.total);
        setSelectedIds([]);
      } catch (error) {
        if (active) message.error(getApiErrorMessage(error, '无法获取操作日志'));
      } finally {
        if (active) setLoading(false);
      }
    };
    void run();
    return () => {
      active = false;
    };
  }, [message, page, query, reloadToken]);

  const search = () => {
    setPage(1);
    setQuery({ ...draft });
    setReloadToken((value) => value + 1);
  };

  const remove = async () => {
    try {
      const response = await deleteOperationLogs(selectedIds);
      message.success(response.message || '删除成功');
      setSelectedIds([]);
      if (records.length === selectedIds.length && page > 1) setPage(page - 1);
      setReloadToken((value) => value + 1);
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败，请稍后重试'));
    }
  };

  const columns: ColumnsType<OperationLog> = [
    { title: '操作人', dataIndex: 'username', width: 130, render: (value) => <span className='log-strong'>{value || '-'}</span> },
    { title: '模块', dataIndex: 'module', width: 120, render: (value) => <Tag>{value}</Tag> },
    { title: '操作', dataIndex: 'action', width: 140 },
    { title: '结果', dataIndex: 'result', width: 95, render: (value: string) => <Tag color={RESULT_META[value]?.color}>{RESULT_META[value]?.text ?? value}</Tag> },
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
        <div className='table-toolbar log-toolbar'>
          <Space wrap>
            <Input value={draft.keyword} onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} onPressEnter={search} prefix={<SearchOutlined />} placeholder='搜索操作人或操作名称' allowClear />
            <Input value={draft.module} onChange={(event) => setDraft({ ...draft, module: event.target.value })} onPressEnter={search} placeholder='模块，例如：用户管理' allowClear style={{ width: 190 }} />
            <Select value={draft.result} onChange={(value) => setDraft({ ...draft, result: value })} options={RESULT_OPTIONS} placeholder='操作结果' allowClear style={{ width: 130 }} />
            <DatePicker.RangePicker showTime value={draft.range} onChange={(value) => setDraft({ ...draft, range: value as [Dayjs, Dayjs] | null })} placeholder={['开始时间', '结束时间']} />
            <Button type='primary' icon={<SearchOutlined />} onClick={search}>
              查询
            </Button>
          </Space>
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((value) => value + 1)}>
              刷新
            </Button>
            <Permission code='system:operatelog:delete'>
              <Popconfirm title='确定删除选中的操作日志？' description='日志删除后不可恢复，且删除动作本身会被记入操作日志。' okText='删除' cancelText='取消' disabled={!selectedIds.length} onConfirm={() => void remove()}>
                <Button danger icon={<DeleteOutlined />} disabled={!selectedIds.length}>
                  删除{selectedIds.length ? `（${selectedIds.length}）` : ''}
                </Button>
              </Popconfirm>
            </Permission>
          </Space>
        </div>
        <Table
          rowKey='id'
          columns={columns}
          dataSource={records}
          loading={loading}
          rowSelection={{ selectedRowKeys: selectedIds, onChange: (keys) => setSelectedIds(keys.map(Number)) }}
          pagination={{ current: page, total, pageSize: PAGE_SIZE, showSizeChanger: false, onChange: setPage, showTotal: (value) => `共 ${value} 条记录` }}
          scroll={{ x: 1050 }}
        />
      </div>
    </div>
  );
}
