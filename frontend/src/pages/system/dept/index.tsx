import { useEffect, useMemo, useState } from 'react';
import { App, Button, Input, Popconfirm, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApartmentOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteDept, getDepts } from '../../../api/dept';
import { Permission } from '../../../permission/Permission';
import type { Dept } from '../../../types/dept';
import { getApiErrorMessage } from '../../../utils/apiError';
import { DeptDialog } from './DeptDialog';
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

export default function DeptPage() {
  const { message } = App.useApp();
  const [depts, setDepts] = useState<Dept[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Dept | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const load = async () => {
    try {
      setLoading(true);
      setDepts(await getDepts());
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法获取部门列表'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const rows = useMemo(() => {
    const match = (dept: Dept) => !keyword.trim() || [dept.deptName, dept.deptCode, dept.leaderName].some((value) => value?.toLowerCase().includes(keyword.toLowerCase()));
    const prune = (items: DeptRow[]): DeptRow[] =>
      items.flatMap((item) => {
        const children = prune(item.children ?? []);
        return match(item) || children.length ? [{ ...item, children }] : [];
      });
    return prune(toTree(depts));
  }, [depts, keyword]);
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
          {row.manageable && <Permission code='system:dept:delete'>
            <Popconfirm
              title='确定删除此部门？'
              description='存在子部门或部门用户时无法删除。'
              okText='删除'
              cancelText='取消'
              onConfirm={() =>
                void deleteDept(row.id)
                  .then(() => {
                    message.success('删除部门成功');
                    void load();
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
        <div className='table-toolbar'>
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} prefix={<SearchOutlined />} placeholder='搜索部门名称、编码或负责人' />
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>
              刷新
            </Button>
            {canCreate && <Permission code='system:dept:add'>
              <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>
                新增部门
              </Button>
            </Permission>}
          </Space>
        </div>
        <Table
          rowKey='id'
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 980 }}
          expandable={{
            defaultExpandAllRows: true,
            indentSize: 18,
            expandIcon: ({ expanded, onExpand, record }) =>
              record.children?.length ? (
                <button type='button' className={`tree-expand-arrow${expanded ? ' is-expanded' : ''}`} aria-label={expanded ? '收起子部门' : '展开子部门'} onClick={(event) => onExpand(record, event)} />
              ) : null,
          }}
        />
      </div>
      <DeptDialog open={dialogOpen} dept={editing} allDepts={depts} onClose={() => setDialogOpen(false)} onSaved={() => void load()} />
    </div>
  );
}
