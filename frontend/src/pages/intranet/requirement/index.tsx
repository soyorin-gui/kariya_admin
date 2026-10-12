import { useRef, useState } from 'react';
import { App, Button, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, EyeOutlined, PlusOutlined, SendOutlined, StopOutlined } from '@ant-design/icons';
import { cancelRequirement, getRequirement, getRequirements, submitRequirement } from './api';
import type { Requirement } from './types';
import { RequirementDialog } from './RequirementDialog';
import { RequirementDetailDrawer } from './RequirementDetailDrawer';
import { NewTransactionDetailDialog } from './NewTransactionDetailDialog';
import { Permission } from '../../../permission/Permission';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { getApiErrorMessage } from '../../../utils/apiError';
import { EllipsisText } from '../../../components/EllipsisText';
import '../transaction/index.css';

type Search = { keyword: string };
const types: Record<number, string> = { 1: '新增交易', 2: '交易变更', 3: '交易调用' };
const statuses: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  1: { label: '审批中', color: 'processing' },
  2: { label: '已通过', color: 'success' },
  3: { label: '已拒绝', color: 'error' },
  4: { label: '已撤销', color: 'default' },
};
export default function RequirementPage() {
  const { message } = App.useApp();
  const tableRef = useRef<SmartTableRef<Search>>(null);
  const [editingId, setEditingId] = useState<number | null | undefined>(undefined);
  const [detail, setDetail] = useState<Requirement>();
  const [newDetailId, setNewDetailId] = useState<number>();
  const showDetail = async (id: number) => { try { setDetail(await getRequirement(id)); } catch (e) { message.error(getApiErrorMessage(e, '无法加载需求详情')); } };
  const columns: ColumnsType<Requirement> = [
    { title: 'OA编号', dataIndex: 'oaNo', width: 130 },
    { title: '需求名称', dataIndex: 'title', width: 220 },
    { title: '需求描述', dataIndex: 'description', width: 260, ellipsis: { showTitle: false }, render: (v) => <EllipsisText value={v} /> },
    { title: '类型', dataIndex: 'requirementType', width: 110, filters: Object.entries(types).map(([value, text]) => ({ text, value })), render: (v) => <Tag color='blue'>{types[v]}</Tag> },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      filters: Object.entries(statuses).map(([value, item]) => ({ text: item.label, value })),
      render: (v) => <Tag color={statuses[v]?.color}>{statuses[v]?.label || v}</Tag>,
    },
    { title: '期望上线时间', dataIndex: 'expectedOnlineDate', width: 140, sorter: true, render: (v) => v || '-' },
    { title: '开发负责人', dataIndex: 'developerName', width: 130, render: (v) => v || '-' },
    { title: '更新时间', dataIndex: 'updatedTime', width: 170, sorter: true, render: (v) => v?.replace('T', ' ') },
    {
      title: '操作',
      fixed: 'right',
      width: 350,
      render: (_, row) => <Space size='middle'><a onClick={() => void showDetail(row.id)}><EyeOutlined /> 详情</a>{row.requirementType === 1 && (row.status === 0 || row.status === 3) && <Permission code='intranet:requirement:update'><a onClick={() => setNewDetailId(row.id)}>明细</a></Permission>}<Permission code='intranet:requirement:update'><a onClick={() => setEditingId(row.id)}><EditOutlined /> 编辑</a></Permission>{row.status === 0 && <Permission code='intranet:requirement:submit'><a onClick={() => void submitRequirement(row.id).then(() => { message.success('需求已提交审批'); tableRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><SendOutlined /> 提交</a></Permission>}{row.status === 1 && <Permission code='intranet:requirement:submit'><a className='danger' onClick={() => void cancelRequirement(row.id).then(() => { message.success('需求已撤销'); tableRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><StopOutlined /> 撤销</a></Permission>}</Space>,
    },
  ];
  return (
    <div className='transaction-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>INTRANET REQUIREMENTS</div>
          <h1 className='page-title'>内网需求管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<Requirement, Search>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '' }}
          request={async ({ page, pageSize, search, filters, sorter }) => {
            const type = Array.isArray(filters?.requirementType) ? filters.requirementType : [];
            const status = Array.isArray(filters?.status) ? filters.status : [];
            const data = await getRequirements({
              pageNum: page,
              pageSize,
              keyword: search.keyword,
              type: type.length ? Number(type[0]) : undefined,
              status: status.length ? Number(status[0]) : undefined,
              sortField: sorter?.field,
              sortOrder: sorter?.order,
            });
            return { list: data.records, total: data.total };
          }}
          onRequestError={(e) => message.error(getApiErrorMessage(e, '无法获取需求列表'))}
          toolbarClassName='table-toolbar'
          searchConfig={[{ name: 'keyword', label: 'OA编号 / 需求名称', type: 'input', width: 260, placeholder: '请输入 OA编号或需求名称' }]}
          toolbarRender={() => (
            <Space wrap>
              <Permission code='intranet:requirement:add'>
                <Button type='primary' icon={<PlusOutlined />} onClick={() => setEditingId(null)}>
                  新建需求
                </Button>
              </Permission>
            </Space>
          )}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (v) => `共 ${v} 条记录` }}
          scroll={{ x: 1600 }}
        />
      </div>
      {editingId !== undefined && <RequirementDialog open id={editingId} onClose={() => setEditingId(undefined)} onSaved={() => tableRef.current?.reload()} />}
      <RequirementDetailDrawer value={detail} onClose={() => setDetail(undefined)} />
      <NewTransactionDetailDialog id={newDetailId} onClose={() => setNewDetailId(undefined)} />
    </div>
  );
}
