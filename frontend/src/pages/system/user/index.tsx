import { useRef, useState } from 'react';
import { App, Button, Input, List, Modal, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, DownloadOutlined, EditOutlined, LaptopOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteUser, exportUsers, getUsers, getUserSessions, removeAllUserSessions, removeUserSession, resetUserPassword } from '../../../api/user';
import type { LoginSessionView } from '../../../api/account';
import { Permission } from '../../../permission/Permission';
import type { UserListItem } from '../../../types/user';
import { downloadBlob } from '../../../utils/download';
import { getApiErrorMessage } from '../../../utils/apiError';
import { UserDialog } from './UserDialog';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { StatusTag } from '../../../components/StatusTag';
import './index.css';

type UserSearch = { keyword: string };

export default function UserPage() {
  const { message, modal } = App.useApp();
  const tableRef = useRef<SmartTableRef<UserSearch>>(null);
  const currentRecordCount = useRef(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<UserListItem | null>(null);
  const [sessionUser, setSessionUser] = useState<UserListItem | null>(null);
  const [sessions, setSessions] = useState<LoginSessionView[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);

  const openDialog = (user: UserListItem | null = null) => {
    setEditing(user);
    setDialogOpen(true);
  };
  const openSessions = async (user: UserListItem) => {
    setSessionUser(user); setSessionsLoading(true);
    try { setSessions(await getUserSessions(user.id)); }
    catch (error) { message.error(getApiErrorMessage(error, '无法加载登录会话')); }
    finally { setSessionsLoading(false); }
  };
  const handleExport = async (keyword: string) => {
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
  const columns: ColumnsType<UserListItem> = [
    { title: '用户名', dataIndex: 'username', width: 130 },
    { title: '姓名', dataIndex: 'realName', width: 110 },
    { title: '手机号', dataIndex: 'phone', width: 145 },
    { title: '状态', dataIndex: 'status', width: 92, render: (value) => <StatusTag status={value === 1 ? 'success' : 'default'} label={value === 1 ? '启用' : '禁用'} /> },
    { title: '角色', dataIndex: 'roleNames', width: 170, render: (value) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 110, render: (value) => value || '-' },
    { title: '创建时间', dataIndex: 'createdTime', width: 170, render: (value) => value?.replace('T', ' ') },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      render: (_, user) => (
        <Space size='middle'>
          {user.manageable && (
            <Permission code='system:user:update'>
              <a onClick={() => openDialog(user)}>
                <EditOutlined /> 编辑
              </a>
            </Permission>
          )}
          {user.manageable && <Permission code='system:user:update'>
            <a onClick={() => void openSessions(user)}><LaptopOutlined /> 会话</a>
          </Permission>}
          {user.resettable && (
            <Permission code='system:user:reset-password'>
              <Popconfirm
                title='确定重置此用户的密码？'
                description='重置后，该用户的现有登录状态将失效。'
                okText='重置'
                cancelText='取消'
                onConfirm={() =>
                  void resetUserPassword(user.id)
                    .then((result) => {
                      modal.info({
                        title: '临时密码',
                        content: (
                          <p>
                            请安全地告知用户临时密码：<strong>{result.data.temporaryPassword}</strong>。该密码只显示一次，登录后必须修改。
                          </p>
                        ),
                      });
                    })
                    .catch((error) => message.error(getApiErrorMessage(error)))
                }
              >
                <a>重置密码</a>
              </Popconfirm>
            </Permission>
          )}
          {user.deletable && (
            <Permission code='system:user:delete'>
              <Popconfirm
                title='确定删除此用户？'
                description='删除后用户将无法登录系统。'
                okText='删除'
                cancelText='取消'
                onConfirm={() =>
                  void deleteUser(user.id)
                    .then(() => {
                      message.success('删除用户成功');
                      const currentPage = tableRef.current?.getQueryParams().page ?? 1;
                      tableRef.current?.reload({ page: currentRecordCount.current === 1 && currentPage > 1 ? currentPage - 1 : currentPage });
                    })
                    .catch((error) => message.error(getApiErrorMessage(error)))
                }
              >
                <a className='danger'>
                  <DeleteOutlined /> 删除
                </a>
              </Popconfirm>
            </Permission>
          )}
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
        <SmartTable<UserListItem, UserSearch>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '' }}
          request={async ({ page, pageSize, search }) => {
            const result = await getUsers({ pageNum: page, pageSize, keyword: search.keyword });
            return { list: result.records, total: result.total };
          }}
          onRequestError={(error) => message.error(getApiErrorMessage(error, '无法获取用户列表'))}
          onDataLoaded={({ list }) => {
            currentRecordCount.current = list.length;
          }}
          toolbarClassName='table-toolbar'
          searchRender={({ search, setSearch, submit }) => (
            <Input value={search.keyword} onChange={(event) => setSearch({ keyword: event.target.value })} onPressEnter={() => submit()} prefix={<SearchOutlined />} placeholder='搜索用户名 / 姓名' />
          )}
          toolbarRender={({ search, submit }) => (
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => submit()}>
                刷新
              </Button>
              <Permission code='system:user:export'>
                <Button icon={<DownloadOutlined />} onClick={() => void handleExport(search.keyword)}>
                  导出
                </Button>
              </Permission>
              <Permission code='system:user:add'>
                <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>
                  新增用户
                </Button>
              </Permission>
            </Space>
          )}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (value) => `共 ${value} 条记录` }}
          scroll={{ x: 1100 }}
        />
      </div>
      <UserDialog open={dialogOpen} userId={editing?.id ?? null} onClose={() => setDialogOpen(false)} onSaved={() => tableRef.current?.reload()} />
      <Modal className='system-dialog' width={760} title={sessionUser ? `登录会话 · ${sessionUser.username}` : '登录会话'} open={sessionUser !== null} footer={null} onCancel={() => setSessionUser(null)} destroyOnHidden>
        <Space style={{ width: '100%', justifyContent: 'flex-end', marginBottom: 12 }}>
          <Popconfirm title='踢出该用户的全部登录会话？' onConfirm={() => sessionUser && void removeAllUserSessions(sessionUser.id).then(() => { message.success('全部会话已退出'); setSessions([]); }).catch((error) => message.error(getApiErrorMessage(error)))}><Button danger>全部退出</Button></Popconfirm>
        </Space>
        <List loading={sessionsLoading} dataSource={sessions} locale={{ emptyText: '暂无登录会话' }} renderItem={(session) => <List.Item actions={[<Popconfirm key='remove' title='踢出该登录会话？' onConfirm={() => sessionUser && void removeUserSession(sessionUser.id, session.id).then(() => { message.success('会话已退出'); setSessions((value) => value.filter((item) => item.id !== session.id)); }).catch((error) => message.error(getApiErrorMessage(error)))}><Button danger type='link'>踢出</Button></Popconfirm>]}>
          <List.Item.Meta title={<Space>{session.authMethod === 'PASSWORD' ? '账号密码' : session.providerKey || '第三方登录'}{session.current && <Tag color='blue'>当前浏览器</Tag>}{session.rememberMe && <Tag>记住我</Tag>}</Space>} description={[session.loginIp || '未知地址', session.lastActiveTime?.replace('T', ' ') || '未知时间', session.userAgent || '未知设备'].join(' · ')} />
        </List.Item>} />
      </Modal>
    </div>
  );
}
