import { useEffect, useState } from 'react';
import { App, Button, Input, Popconfirm, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, DownloadOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteUser, exportUsers, getUsers, resetUserPassword } from '../../../api/user';
import { Permission } from '../../../permission/Permission';
import type { User } from '../../../types/user';
import { downloadBlob } from '../../../utils/download';
import { getApiErrorMessage } from '../../../utils/apiError';
import { UserDialog } from './UserDialog';
import './index.css';

export default function UserPage() {
  const { message, modal } = App.useApp();
  const [records, setRecords] = useState<User[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);

  const load = async (nextPage = page, search = keyword) => {
    try {
      setLoading(true);
      const result = await getUsers({ pageNum: nextPage, pageSize: 10, keyword: search });
      setRecords(result.records);
      setTotal(result.total);
      setPage(nextPage);
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法获取用户列表'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);

  const openDialog = (user: User | null = null) => {
    setEditing(user);
    setDialogOpen(true);
  };
  const search = () => void load(1, keyword);
  const handleExport = async () => {
    let hide: (() => void) | undefined;
    try {
      hide = message.loading('正在生成 Excel 文件...', 0);
      const response = await exportUsers(keyword);
      downloadBlob(response.data as Blob, `用户列表_${new Date().toISOString().slice(0, 10)}.xlsx`);
      message.success('用户数据导出成功');
    } catch (error) {
      message.error(getApiErrorMessage(error, '导出失败，请稍后重试'));
    } finally {
      hide?.();
    }
  };
  const columns: ColumnsType<User> = [
    { title: '用户名', dataIndex: 'username', width: 130 },
    { title: '姓名', dataIndex: 'realName', width: 110 },
    { title: '手机号', dataIndex: 'phone', width: 145 },
    { title: '状态', dataIndex: 'status', width: 92, render: (value) => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '启用' : '禁用'}</Tag> },
    { title: '角色', dataIndex: 'roleNames', width: 170, render: (value) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (value) => value || '-' },
    { title: '创建时间', dataIndex: 'createdTime', width: 170, render: (value) => value?.replace('T', ' ') },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      render: (_, user) => (
        <Space size='middle'>
          {user.manageable && <Permission code='system:user:update'><a onClick={() => openDialog(user)}>
            <EditOutlined /> 编辑
          </a></Permission>}
          {user.resettable && <Permission code='system:user:reset-password'><Popconfirm
            title='确定重置此用户的密码？'
            description='重置后，该用户的现有登录状态将失效。'
            okText='重置'
            cancelText='取消'
            onConfirm={() => void resetUserPassword(user.id)
              .then((result) => { modal.info({ title: '临时密码', content: <p>请安全地告知用户临时密码：<strong>{result.data.temporaryPassword}</strong>。该密码只显示一次，登录后必须修改。</p> }); })
              .catch((error) => message.error(getApiErrorMessage(error)))}
          ><a>重置密码</a></Popconfirm></Permission>}
          {user.deletable && <Permission code='system:user:delete'><Popconfirm
            title='确定删除此用户？'
            description='删除后用户将无法登录系统。'
            okText='删除'
            cancelText='取消'
            onConfirm={() =>
              void deleteUser(user.id)
                .then(() => {
                  message.success('删除用户成功');
                  void load(records.length === 1 && page > 1 ? page - 1 : page);
                })
                .catch((error) => message.error(getApiErrorMessage(error)))
            }
          >
            <a className='danger'>
              <DeleteOutlined /> 删除
            </a>
          </Popconfirm></Permission>}
        </Space>
      ),
    },
  ];

  return (
    <div className='user-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>用户管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <div className='table-toolbar'>
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={search} prefix={<SearchOutlined />} placeholder='搜索用户名 / 姓名' />
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={search}>
              刷新
            </Button>
            <Permission code='system:user:export'>
              <Button icon={<DownloadOutlined />} onClick={() => void handleExport()}>
                导出
              </Button>
            </Permission>
            <Permission code='system:user:add'>
              <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>
                新增用户
              </Button>
            </Permission>
          </Space>
        </div>
        <Table
          rowKey='id'
          columns={columns}
          dataSource={records}
          loading={loading}
          pagination={{ current: page, total, pageSize: 10, showSizeChanger: false, onChange: (nextPage) => void load(nextPage), showTotal: (value) => `共 ${value} 条记录` }}
          scroll={{ x: 1100 }}
        />
      </div>
      <UserDialog open={dialogOpen} user={editing} onClose={() => setDialogOpen(false)} onSaved={() => void load()} />
    </div>
  );
}
