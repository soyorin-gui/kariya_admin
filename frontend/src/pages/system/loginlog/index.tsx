import { useEffect, useState } from 'react';
import { App, Button, DatePicker, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { DeleteOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteLoginLogs, getLoginLogs } from '../../../api/loginLog';
import { Permission } from '../../../permission/Permission';
import type { LoginLog, LogResult } from '../../../types/log';
import { getApiErrorMessage } from '../../../utils/apiError';
import './index.css';

const RESULT_META: Record<LogResult, { text: string; color: string }> = {
  SUCCESS: { text: '成功', color: 'success' },
  FAILURE: { text: '失败', color: 'error' },
  LOCKED: { text: '已锁定', color: 'warning' },
};
const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
  { value: 'LOCKED', label: '已锁定' },
];
/** 与后端 LocalDateTime 的 @DateTimeFormat(iso = DATE_TIME) 对齐。 */
const TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';
const PAGE_SIZE = 10;

interface LogFilters {
  username: string;
  result?: LogResult;
  range: [Dayjs, Dayjs] | null;
}

export default function LoginLogPage() {
  const { message } = App.useApp();
  const [records, setRecords] = useState<LoginLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  /** 输入框里的草稿条件；点"查询"后才提交为 query，避免每敲一个字都打一次接口。 */
  const [draft, setDraft] = useState<LogFilters>({ username: '', range: null });
  const [query, setQuery] = useState<LogFilters>(draft);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;
    const run = async () => {
      try {
        setLoading(true);
        const data = await getLoginLogs({
          pageNum: page,
          pageSize: PAGE_SIZE,
          username: query.username.trim() || undefined,
          result: query.result,
          beginTime: query.range?.[0]?.format(TIME_FORMAT),
          endTime: query.range?.[1]?.format(TIME_FORMAT),
        });
        // 连续点查询/翻页时，先发出的慢响应可能后回来，用 active 丢掉过期结果。
        if (!active) return;
        setRecords(data.records);
        setTotal(data.total);
        setSelectedIds([]);
      } catch (error) {
        if (active) message.error(getApiErrorMessage(error, '无法获取登录日志'));
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
      const response = await deleteLoginLogs(selectedIds);
      message.success(response.message || '删除成功');
      setSelectedIds([]);
      // 删掉的是当前页最后几条时往前退一页，否则留在当前页刷新即可。
      if (records.length === selectedIds.length && page > 1) setPage(page - 1);
      setReloadToken((value) => value + 1);
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败，请稍后重试'));
    }
  };

  const columns: ColumnsType<LoginLog> = [
    { title: '用户名', dataIndex: 'username', width: 140, render: (value) => <span className='log-strong'>{value}</span> },
    { title: '结果', dataIndex: 'result', width: 100, render: (value: LogResult) => <Tag color={RESULT_META[value]?.color}>{RESULT_META[value]?.text ?? value}</Tag> },
    { title: '说明', dataIndex: 'message', width: 230, ellipsis: true, render: (value) => value || '-' },
    { title: '登录 IP', dataIndex: 'loginIp', width: 150, render: (value) => <span className='log-mono'>{value || '-'}</span> },
    { title: '客户端', dataIndex: 'userAgent', width: 260, ellipsis: true, render: (value) => value || '-' },
    { title: '登录时间', dataIndex: 'loginTime', width: 180, render: (value) => <span className='log-time'>{value?.replace('T', ' ') || '-'}</span> },
  ];

  return (
    <div className='log-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>登录日志</h1>
        </div>
      </div>
      <div className='table-card'>
        <div className='table-toolbar log-toolbar'>
          <Space wrap>
            <Input value={draft.username} onChange={(event) => setDraft({ ...draft, username: event.target.value })} onPressEnter={search} prefix={<SearchOutlined />} placeholder='搜索用户名' allowClear />
            <Select value={draft.result} onChange={(value) => setDraft({ ...draft, result: value })} options={RESULT_OPTIONS} placeholder='登录结果' allowClear style={{ width: 140 }} />
            <DatePicker.RangePicker showTime value={draft.range} onChange={(value) => setDraft({ ...draft, range: value as [Dayjs, Dayjs] | null })} placeholder={['开始时间', '结束时间']} />
            <Button type='primary' icon={<SearchOutlined />} onClick={search}>
              查询
            </Button>
          </Space>
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => setReloadToken((value) => value + 1)}>
              刷新
            </Button>
            <Permission code='system:loginlog:delete'>
              <Popconfirm title='确定删除选中的登录日志？' description='日志删除后不可恢复，且删除动作本身会被记入操作日志。' okText='删除' cancelText='取消' disabled={!selectedIds.length} onConfirm={() => void remove()}>
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
          scroll={{ x: 1100 }}
        />
      </div>
    </div>
  );
}
