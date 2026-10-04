import { useRef, useState } from 'react';
import type { Key } from 'react';
import { App, Button, Input, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApartmentOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteDept, getDepts } from '../../../api/dept';
import { Permission } from '../../../permission/Permission';
import type { Dept } from '../../../types/dept';
import { getApiErrorMessage } from '../../../utils/apiError';
import { DeptDialog } from './DeptDialog';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import './index.css';

interface DeptRow extends Dept {
  children?: DeptRow[];
}
function toTree(depts: Dept[], parentId = 0): DeptRow[] {
  return depts
    .filter((dept) => dept.parentId === parentId)
    .map((dept) => {
      const children = toTree(depts, dept.id);
      return children.length ? { ...dept, children } : { ...dept };
    });
}
function filterTree(depts: Dept[], keyword: string): DeptRow[] {
  const normalized = keyword.trim().toLowerCase();
  const matches = (dept: Dept) => !normalized || [dept.deptName, dept.deptCode, dept.leaderName].some((value) => value?.toLowerCase().includes(normalized));
  const prune = (items: DeptRow[]): DeptRow[] => items.flatMap((item) => {
    const children = prune(item.children ?? []);
    return matches(item) || children.length ? [{ ...item, children: children.length ? children : undefined }] : [];
  });
  return prune(toTree(depts));
}
type DeptSearch = { keyword: string };

export default function DeptPage() {
  const { message } = App.useApp();
  const tableRef = useRef<SmartTableRef<DeptSearch>>(null);
  const [depts, setDepts] = useState<Dept[]>([]);
  const [editing, setEditing] = useState<Dept | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [expandedRowKeys, setExpandedRowKeys] = useState<readonly Key[]>();
  const openDialog = (dept: Dept | null = null) => {
    setEditing(dept);
    setDialogOpen(true);
  };
  const canCreate = depts.some((dept) => dept.canCreateChildren);
  const columns: ColumnsType<DeptRow> = [
    {
      title: '部门名称',
      dataIndex: 'deptName',
      width: 220,
      render: (value, row) => (
        <Space>
          <ApartmentOutlined />
          {value}
          {row.builtin === 1 && <Tag color='blue'>根部门</Tag>}
        </Space>
      ),
    },
    { title: '部门编码', dataIndex: 'deptCode', width: 160, render: (value) => <code>{value}</code> },
    { title: '负责人', dataIndex: 'leaderName', width: 135, render: (value) => value || '-' },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    { title: '状态', dataIndex: 'status', width: 95, render: (value) => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag> },
    { title: '创建时间', dataIndex: 'createdTime', width: 175, render: (value) => value?.replace('T', ' ') || '-' },
    {
      title: '操作',
      width: 170,
      fixed: 'right',
      render: (_, row) => (
        <Space size='middle'>
          {row.manageable && <Permission code='system:dept:update'>
            <a onClick={() => openDialog(row)}>
              <EditOutlined /> 编辑
            </a>
          </Permission>}
          {row.deletable && <Permission code='system:dept:delete'>
            <Popconfirm
              title='确定删除此部门？'
              description='存在子部门或部门用户时无法删除。'
              okText='删除'
              cancelText='取消'
              onConfirm={() =>
                void deleteDept(row.id)
                  .then(() => {
                    message.success('删除部门成功');
                    tableRef.current?.reload();
                  })
                  .catch((error) => message.error(getApiErrorMessage(error)))
              }
            >
              <a className='danger'>
                <DeleteOutlined /> 删除
              </a>
            </Popconfirm>
          </Permission>}
        </Space>
      ),
    },
  ];
  return (
    <div className='dept-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>部门管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<DeptRow, DeptSearch>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '' }}
          request={async ({ search }) => {
            const list = await getDepts();
            setDepts(list);
            const tree = filterTree(list, search.keyword);
            return { list: tree, total: tree.length };
          }}
          onRequestError={(error) => message.error(getApiErrorMessage(error, '无法获取部门列表'))}
          toolbarClassName='table-toolbar'
          searchRender={({ search, setSearch, submit }) => <Input value={search.keyword} onChange={(event) => setSearch({ keyword: event.target.value })} onPressEnter={() => submit()} prefix={<SearchOutlined />} placeholder='搜索部门名称、编码或负责人' />}
          toolbarRender={({ submit }) => <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => submit()}>刷新</Button>
            {canCreate && <Permission code='system:dept:add'><Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>新增部门</Button></Permission>}
          </Space>}
          pagination={false}
          scroll={{ x: 980 }}
          expandable={{
            expandedRowKeys: expandedRowKeys ?? depts.filter((dept) => dept.parentId === 0).map((dept) => dept.id),
            onExpandedRowsChange: setExpandedRowKeys,
            indentSize: 18,
            expandIcon: ({ expanded, onExpand, record }) =>
              record.children?.length ? (
                <button type='button' className={`tree-expand-arrow${expanded ? ' is-expanded' : ''}`} aria-label={expanded ? '收起子部门' : '展开子部门'} onClick={(event) => onExpand(record, event)} />
              ) : null,
          }}
        />
      </div>
      <DeptDialog open={dialogOpen} deptId={editing?.id ?? null} allDepts={depts} onClose={() => setDialogOpen(false)} onSaved={() => tableRef.current?.reload()} />
    </div>
  );
}
