import { useEffect, useState } from 'react';
import { App, Button, Input, Popconfirm, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteRole, getRoles } from '../../../api/role';
import { Permission } from '../../../permission/Permission';
import type { Role } from '../../../types/role';
import { getApiErrorMessage } from '../../../utils/apiError';
import { RoleDialog } from './RoleDialog';
import { RolePermissionDialog } from './RolePermissionDialog';
import './index.css';

const scopeLabel: Record<Role['dataScope'], string> = { ALL: '全部数据', DEPT_AND_CHILDREN: '本部门及下级', DEPT: '本部门', SELF: '仅本人' };

export default function RolePage() {
  const { message } = App.useApp();
  const [records, setRecords] = useState<Role[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [editing, setEditing] = useState<Role | null>(null);
  const [granting, setGranting] = useState<Role | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  const load = async () => {
    try {
      setLoading(true);
      setRecords(await getRoles(keyword));
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法获取角色列表'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const openDialog = (role: Role | null = null) => {
    setEditing(role);
    setDialogOpen(true);
  };
  const columns: ColumnsType<Role> = [
    {
      title: '角色名称',
      dataIndex: 'roleName',
      width: 170,
      render: (value, record) => (
        <Space>
          {value}
          {record.builtin === 1 && <Tag color='blue'>内置</Tag>}
        </Space>
      ),
    },
    { title: '角色标识', dataIndex: 'roleCode', width: 180, render: (value) => <code>{value}</code> },
    { title: '数据范围', dataIndex: 'dataScope', width: 150, render: (value: Role['dataScope']) => scopeLabel[value] },
    { title: '用户数', dataIndex: 'userCount', width: 96 },
    { title: '状态', dataIndex: 'status', width: 95, render: (value) => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '启用' : '禁用'}</Tag> },
    { title: '创建时间', dataIndex: 'createdTime', width: 175, render: (value) => value?.replace('T', ' ') || '-' },
    {
      title: '操作',
      width: 245,
      fixed: 'right',
      render: (_, record) => (
        <Space size='middle'>
          <Permission code='system:role:grant'>
            <a onClick={() => setGranting(record)}>
              <KeyOutlined /> 授权
            </a>
          </Permission>
          <Permission code='system:role:update'>
            <a onClick={() => openDialog(record)}>
              <EditOutlined /> 编辑
            </a>
          </Permission>
          <Permission code='system:role:delete'>
            <Popconfirm
              title='确定删除此角色？'
              description='已分配给用户的角色无法删除。'
              okText='删除'
              cancelText='取消'
              onConfirm={() =>
                void deleteRole(record.id)
                  .then(() => {
                    message.success('删除角色成功');
                    void load();
                  })
                  .catch((error) => message.error(getApiErrorMessage(error)))
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
    <div className='role-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>角色管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <div className='table-toolbar'>
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={() => void load()} prefix={<SearchOutlined />} placeholder='搜索角色名称 / 标识' />
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>
              刷新
            </Button>
            <Permission code='system:role:add'>
              <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>
                新增角色
              </Button>
            </Permission>
          </Space>
        </div>
        <Table rowKey='id' columns={columns} dataSource={records} loading={loading} pagination={false} scroll={{ x: 1040 }} />
      </div>
      <RoleDialog open={dialogOpen} role={editing} onClose={() => setDialogOpen(false)} onSaved={() => void load()} />
      <RolePermissionDialog open={granting !== null} role={granting} onClose={() => setGranting(null)} onSaved={() => void load()} />
    </div>
  );
}
