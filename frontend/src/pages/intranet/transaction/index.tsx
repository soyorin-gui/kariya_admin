import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Button, Descriptions, Drawer, Input, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteTransaction, getTransaction, getTransactions } from './api';
import type { Transaction, TransactionListItem } from './types';
import { TransactionDialog } from './TransactionDialog';
import { Permission } from '../../../permission/Permission';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { getApiErrorMessage } from '../../../utils/apiError';
import './index.css';

type Search = { keyword: string; status?: number };
const statuses: Record<number, { label: string; color: string }> = {
  0: { label: '评估中', color: 'default' },
  1: { label: '开发中', color: 'processing' },
  2: { label: '已投产', color: 'success' },
  3: { label: '已下线', color: 'error' },
};
const printMode: Record<number, string> = { 0: '无', 1: '异步', 2: '同步' };
export default function TransactionPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const tableRef = useRef<SmartTableRef<Search>>(null);
  const [editingId, setEditingId] = useState<number | null | undefined>(undefined);
  const [detail, setDetail] = useState<Transaction>();
  const [detailOpen, setDetailOpen] = useState(false);
  const showDetail = async (id: number) => {
    try {
      setDetail(await getTransaction(id));
      setDetailOpen(true);
    } catch (e) {
      message.error(getApiErrorMessage(e, '无法加载交易详情'));
    }
  };
  const columns: ColumnsType<TransactionListItem> = [
    { title: '交易名称', dataIndex: 'transactionName', width: 180 },
    { title: '交易编码', dataIndex: 'transactionCode', width: 150, render: (v) => <code>{v}</code> },
    { title: '状态', dataIndex: 'status', width: 100, filters: Object.entries(statuses).map(([value, item]) => ({ text: item.label, value })), render: (v) => <Tag color={statuses[v]?.color}>{statuses[v]?.label || v}</Tag> },
    { title: 'ESF 服务名', dataIndex: 'esfServiceName', width: 150 },
    { title: '操作名称', dataIndex: 'esfServiceOperationName', width: 170 },
    { title: '标签', dataIndex: 'label', width: 100, render: (v) => (v ? <Tag>{v}</Tag> : '-') },
    { title: '业务对接人', dataIndex: 'businessContact', width: 130, render: (v) => v || '-' },
    { title: '更新时间', dataIndex: 'updatedTime', width: 170, sorter: true, render: (v) => v?.replace('T', ' ') },
    {
      title: '操作',
      fixed: 'right',
      width: 250,
      render: (_, row) => (
        <Space size='middle'>
          <a onClick={() => void showDetail(row.id)}>
            <EyeOutlined /> 详情
          </a>
          <a onClick={() => void navigate({ pathname: '/intranet/transaction/fields', search: `?transactionId=${row.id}` })}>字段设计</a>
          <Permission code='intranet:transaction:update'>
            <a onClick={() => setEditingId(row.id)}>
              <EditOutlined /> 编辑
            </a>
          </Permission>
          <Permission code='intranet:transaction:delete'>
            <Popconfirm
              title='确定删除此交易？'
              description='删除后不可恢复。'
              onConfirm={() =>
                void deleteTransaction(row.id)
                  .then(() => {
                    message.success('删除交易成功');
                    tableRef.current?.reload();
                  })
                  .catch((e) => message.error(getApiErrorMessage(e)))
              }
            >
              <a className='danger'>
                <DeleteOutlined /> 删除
              </a>
            </Popconfirm>
          </Permission>
        </Space>
      ),
    },
  ];
  return (
    <div className='transaction-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>INTRANET ASSETS</div>
          <h1 className='page-title'>交易资产管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<TransactionListItem, Search>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '' }}
          request={async ({ page, pageSize, search, filters, sorter }) => {
            const selectedStatus = filters.status;
            const data = await getTransactions({ pageNum: page, pageSize, keyword: search.keyword, status: selectedStatus ? Number((selectedStatus as Array<string | number>)[0]) : undefined, sortField: sorter?.field, sortOrder: sorter?.order });
            return { list: data.records, total: data.total };
          }}
          onRequestError={(e) => message.error(getApiErrorMessage(e, '无法获取交易列表'))}
          toolbarClassName='table-toolbar'
          searchRender={({ search, setSearch, submit }) => (
            <Space wrap>
              <Input
                value={search.keyword}
                onChange={(e) => setSearch({ ...search, keyword: e.target.value })}
                onPressEnter={() => submit()}
                prefix={<SearchOutlined />}
                placeholder='搜索交易名称、编码或 ESF 服务名'
              />
            </Space>
          )}
          toolbarRender={({ submit }) => (
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => submit()}>
                刷新
              </Button>
              <Permission code='intranet:transaction:add'>
                <Button type='primary' icon={<PlusOutlined />} onClick={() => setEditingId(null)}>
                  新增交易
                </Button>
              </Permission>
            </Space>
          )}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (v) => `共 ${v} 条记录` }}
          scroll={{ x: 1300 }}
        />
      </div>
      <TransactionDialog open={editingId !== undefined} id={editingId ?? null} onClose={() => setEditingId(undefined)} onSaved={() => tableRef.current?.reload()} />
      <Drawer rootClassName='transaction-detail-drawer' title={detail ? `交易详情 · ${detail.transactionName}` : '交易详情'} width={680} open={detailOpen} onClose={() => setDetailOpen(false)}>
        <Descriptions className='transaction-detail-descriptions' bordered column={1} size='small'>
          {detail && (
            <>
              <Descriptions.Item label='交易编码'>
                <code>{detail.transactionCode}</code>
              </Descriptions.Item>
              <Descriptions.Item label='交易状态'>{statuses[detail.status]?.label || detail.status}</Descriptions.Item>
              <Descriptions.Item label='ESF 服务'>{detail.esfServiceName}</Descriptions.Item>
              <Descriptions.Item label='ESF 操作'>
                {detail.esfServiceOperationName}（{detail.esfServiceOperationId}）
              </Descriptions.Item>
              <Descriptions.Item label='ESF 地址'>{detail.esfServiceAddress}</Descriptions.Item>
              <Descriptions.Item label='标签'>{detail.label || '-'}</Descriptions.Item>
              <Descriptions.Item label='业务对接人'>{detail.businessContact || '-'}</Descriptions.Item>
              <Descriptions.Item label='数据时效性'>{detail.dataTimeliness || '-'}</Descriptions.Item>
              <Descriptions.Item label='查询范围'>{detail.queryScope || '-'}</Descriptions.Item>
              <Descriptions.Item label='排序规则'>{detail.sortRule || '-'}</Descriptions.Item>
              <Descriptions.Item label='数据校验范围'>{detail.dataValidationScope || '-'}</Descriptions.Item>
              <Descriptions.Item label='打印文件'>{printMode[detail.printFileMode]}</Descriptions.Item>
              {detail.printFileMode !== 0 && (
                <>
                  <Descriptions.Item label='文件生成范围'>{detail.fileGenerationScope || '-'}</Descriptions.Item>
                  <Descriptions.Item label='文件名称规则'>{detail.fileNameRule || '-'}</Descriptions.Item>
                </>
              )}
              <Descriptions.Item label='交易说明'>{detail.description || '-'}</Descriptions.Item>
            </>
          )}
        </Descriptions>
      </Drawer>
    </div>
  );
}
