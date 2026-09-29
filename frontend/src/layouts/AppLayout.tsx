import { useEffect, useMemo, useRef, useState } from 'react';
import type { ItemType } from 'antd/es/menu/interface';
import { App, Avatar, Badge, Breadcrumb, Button, Dropdown, Form, Input, Layout, List, Menu, Modal, Popover, Space, Tooltip } from 'antd';
import { BellOutlined, LockOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, SearchOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { SiderBrand } from './SiderBrand';
import { AiAssistant } from '../components/ai/AiAssistant';
import { Permission } from '../permission/Permission';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { clearSession } from '../store/authSlice';
import { logout } from '../api/auth';
import { changeOwnPassword } from '../api/user';
import { getApiErrorMessage } from '../utils/apiError';
import { PASSWORD_MESSAGE, PASSWORD_PATTERN } from '../utils/passwordPolicy';
import type { MenuRoute } from '../types/auth';
import { ancestorMenuKeys, findMenuByPath, firstAvailablePath, menuBreadcrumb, navigateTarget } from '../router/menu';
import { resolveMenuIcon } from '../router/iconRegistry';
import { UserActivityManager } from '../services/UserActivityManager';
import { RealtimeClient } from '../services/RealtimeClient';
import type { SystemNotification } from '../api/notifications';
import { loadNotifications, readAllNotifications, readNotification } from '../store/notificationSlice';
import { SystemSettings } from '../components/SystemSettings';
import '../pages/system/shared.css';

const { Sider, Header, Content } = Layout;
const HOME_ROUTE = '/home';

/**
 * 侧边栏完全由后端菜单树构建，不再有任何硬编码的兜底菜单。
 *
 * 之前这里留了一份写死的 tree 作为 menus 为空时的兜底，问题在于：那份菜单展示的是"全站有什么"，
 * 而不是"你能访问什么"，用户点进去只会撞 403。menus 在 AuthGuard 加载完 /auth/me 之前不会被渲染，
 * 所以那个兜底本来也不会被真正用到，删掉更诚实。
 *
 * 两个容易忽略的点：
 *   - visible === 0 的菜单不进侧边栏（这就是菜单管理里"隐藏"开关的作用）。
 *   - 图标走 iconRegistry 白名单，未登记的图标名回退为默认图标而不是渲染空白。
 */
function buildMenu(routes: MenuRoute[]): ItemType[] {
  const visible = routes.filter((menu) => menu.menuType !== 'BUTTON' && menu.visible !== 0);
  const children = (parentId: number): ItemType[] => {
    const items = visible.filter((menu) => menu.parentId === parentId);
    // 目录下没有任何可见子项时直接不显示，避免出现点开是空的空目录
    return items.flatMap((menu) => {
      const nested = children(menu.id);
      if (menu.menuType === 'DIR' && nested.length === 0) return [];
      return [
        {
          key: menu.routePath ?? `menu-${menu.id}`,
          icon: resolveMenuIcon(menu.icon),
          label: menu.menuType === 'DIR' ? menu.menuName : <Link to={menu.routePath ?? '#'}>{menu.menuName}</Link>,
          children: nested.length ? nested : undefined,
        },
      ];
    });
  };
  return children(0);
}
export function AppLayout() {
  const { message } = App.useApp();
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [siderCollapsed, setSiderCollapsed] = useState(() => {
    try {
      return localStorage.getItem('lbl-sider-collapsed') === 'true';
    } catch {
      return false;
    }
  });
  const [changingPassword, setChangingPassword] = useState(false);
  const passwordSubmitLocked = useRef(false);
  const [passwordForm] = Form.useForm<{ oldPassword: string; newPassword: string; confirmPassword: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const displayName = user?.realName?.trim() || user?.username || '管理员';
  const menus = useAppSelector((s) => s.auth.menus);
  /**
   * 通知数据来自全局 store（见 store/notificationSlice），顶栏与消息中心<b>共用一份</b>：
   * 在消息中心点"全部标为已读"后，这里的小红点会立刻归零，不再依赖下一次实时事件或
   * 切换浏览器标签页。此前这里是自己的 useState，两边各改各的，表现为"读完了红点还在"。
   */
  const notifications = useAppSelector((s) => s.notifications.items);
  const unreadCount = useAppSelector((s) => s.notifications.unreadCount);
  const items = useMemo(() => buildMenu(menus), [menus]);
  const toggleSider = () => {
    setSiderCollapsed((current) => {
      try {
        localStorage.setItem('lbl-sider-collapsed', String(!current));
      } catch {
        /* 隐私模式下仍允许本次会话切换。 */
      }
      return !current;
    });
  };
  // 仅在用户确实进行交互时续期服务端会话。服务端仍会执行空闲超时限制，
  // 对“记住我”会话还会执行 14 天的绝对有效期限制。
  useEffect(() => {
    const activity = new UserActivityManager();
    activity.start();
    return () => activity.stop();
  }, []);
  useEffect(() => {
    void dispatch(loadNotifications());
    const realtime = new RealtimeClient((event) => {
      if (event.type === 'notification.created' || event.type === 'approval.request.updated') void dispatch(loadNotifications());
    });
    realtime.start();
    const onVisible = () => {
      if (document.visibilityState === 'visible') void dispatch(loadNotifications());
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      realtime.stop();
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [dispatch]);
  const openNotification = async (item: SystemNotification) => {
    // 标记已读由 store 统一处理：本地这一条变已读、未读数减一，消息中心页同步生效。
    if (!item.readTime) await dispatch(readNotification(item.id));
    setNotificationOpen(false);
    navigate(`/account/notifications${item.businessId ? `?businessId=${item.businessId}` : ''}`);
  };
  const notificationPanel = (
    <div className='notification-panel'>
      <div className='notification-panel-head'>
        <strong>消息通知</strong>
        <Button type='link' size='small' onClick={() => void dispatch(readAllNotifications())}>
          全部已读
        </Button>
      </div>
      <div className='notification-panel-body'>
        <List
          size='small'
          dataSource={notifications}
          locale={{ emptyText: '暂无消息' }}
          renderItem={(item) => (
            <List.Item onClick={() => void openNotification(item)} style={{ cursor: 'pointer', paddingInline: 4 }}>
              <List.Item.Meta
                title={
                  <Space size={6}>
                    {item.title}
                    {!item.readTime && <Badge status='processing' />}
                  </Space>
                }
                description={
                  <>
                    <div>{item.content}</div>
                    <small>{item.createdTime?.replace('T', ' ')}</small>
                  </>
                }
              />
            </List.Item>
          )}
        />
      </div>
      <Button
        type='link'
        block
        onClick={() => {
          setNotificationOpen(false);
          navigate('/account/notifications');
        }}
      >
        查看全部消息
      </Button>
    </div>
  );
  // 展开的目录不再写死：按当前地址在菜单树里的祖先链自动展开，并保留用户手动展开的其他目录。
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const ancestors = useMemo(() => ancestorMenuKeys(menus, location.pathname), [menus, location.pathname]);
  useEffect(() => {
    setOpenKeys((previous) => Array.from(new Set([...previous, ...ancestors])));
  }, [ancestors]);
  /**
   * 顶栏「首页」入口改为从菜单树取，不再写死 HomeOutlined。
   * 图标用首页菜单配置的图标（在菜单管理里改了这里跟着变）；跳转目标同理。
   * 万一当前角色没有被授予首页，退化为「我的第一项可见页面」，避免点一下直接撞 403。
   */
  const landing = useMemo(() => {
    const grantedHome = findMenuByPath(menus, HOME_ROUTE);
    if (grantedHome) return grantedHome;
    const fallbackPath = firstAvailablePath(menus);
    return fallbackPath ? findMenuByPath(menus, fallbackPath) : undefined;
  }, [menus]);
  const exit = async () => {
    try {
      await logout();
    } finally {
      dispatch(clearSession());
      navigate('/login');
    }
  };
  const submitPassword = async () => {
    if (passwordSubmitLocked.current) return;
    try {
      const values = await passwordForm.validateFields();
      passwordSubmitLocked.current = true;
      setChangingPassword(true);
      await changeOwnPassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('密码已修改，请重新登录');
      dispatch(clearSession());
      navigate('/login', { replace: true });
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '修改密码失败'));
    } finally {
      passwordSubmitLocked.current = false;
      setChangingPassword(false);
    }
  };
  const crumb = menuBreadcrumb(location.pathname, menus).map((title) => ({ title }));
  return (
    <Layout className='app-shell'>
      <Sider
        width={240}
        collapsedWidth={72}
        collapsed={siderCollapsed}
        theme='light'
        className='app-sider'
        breakpoint='lg'
        onBreakpoint={(broken) => {
          if (broken) setSiderCollapsed(true);
        }}
      >
        <SiderBrand />
        <Menu mode='inline' selectedKeys={[location.pathname]} openKeys={openKeys} onOpenChange={setOpenKeys} items={items} />
        <div className='sider-foot'>
          CRUD · BUG · CV
          <br />
          <span>Kariya的个人开发工作台</span>
        </div>
      </Sider>
      <Layout className='app-main-layout'>
        <Header className='app-header'>
          <div className='header-navigation'>
            <Tooltip title={siderCollapsed ? '展开菜单' : '收起菜单'}>
              <Button
                className='sider-trigger'
                type='text'
                shape='circle'
                aria-label={siderCollapsed ? '展开菜单' : '收起菜单'}
                icon={siderCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={toggleSider}
              />
            </Tooltip>
            {/*
              没有可用落点时整条入口不渲染：以前回退成写死的 /home，而账号可能压根没有
              这个页面（未配置/未授权），点一下就是 404/403。首页入口宁可不显示，
              也不要给一个必然打不开的地址。
            */}
            {landing && (
              <Link to={navigateTarget(landing.routePath ?? HOME_ROUTE)} className='header-home'>
                {/*
                  图标必须和侧边栏同一项完全一致，所以两边都走 resolveMenuIcon，
                  连"图标没配时回退成哪个图标"也保持一致（都回退到 MenuOutlined）。
                  之前这里写的是 landing?.icon ? resolveMenuIcon(...) : <HomeOutlined />，
                  一旦首页菜单的图标是空的，侧边栏回退成 MenuOutlined、顶栏却是 HomeOutlined，
                  看起来就是"两个首页图标不一样"。
                */}
                {resolveMenuIcon(landing.icon)} 首页
              </Link>
            )}
            {crumb.length > 0 && <Breadcrumb items={crumb} />}
          </div>
          <Space size={20}>
            <Input className='quick-search' prefix={<SearchOutlined />} placeholder='搜索功能、文档或快捷操作...' />
            <Popover trigger='click' placement='bottomRight' open={notificationOpen} onOpenChange={setNotificationOpen} content={notificationPanel}>
              <Badge count={unreadCount} size='small' overflowCount={99}>
                <Button type='text' shape='circle' aria-label='消息通知' icon={<BellOutlined />} />
              </Badge>
            </Popover>
            <Tooltip title='系统设置'>
              <Button type='text' shape='circle' aria-label='系统设置' icon={<SettingOutlined />} onClick={() => setSettingsOpen(true)} />
            </Tooltip>
            <Dropdown
              menu={{
                items: [
                  { key: 'account', icon: <UserOutlined />, label: '登录与安全', onClick: () => navigate('/account/security') },
                  ...(user?.hasPassword ? [{ key: 'password', icon: <LockOutlined />, label: '修改密码', onClick: () => setPasswordOpen(true) }] : []),
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: exit },
                ],
              }}
            >
              <Space className='user-menu'>
                <Avatar style={{ backgroundColor: 'var(--brand)' }}>{displayName.slice(0, 1).toUpperCase()}</Avatar>
                <span>{displayName}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content className='app-content'>
          <Outlet />
        </Content>
      </Layout>
      <Permission code='agent:chat:use'>
        <AiAssistant />
      </Permission>
      <SystemSettings open={settingsOpen} onClose={() => setSettingsOpen(false)} />
      <Modal
        className='system-dialog'
        width={560}
        title={<div className='system-dialog-title'>{user?.passwordChangeRequired ? '密码需要更新' : '修改密码'}</div>}
        open={Boolean(user?.passwordChangeRequired || passwordOpen)}
        closable={!user?.passwordChangeRequired && !changingPassword}
        maskClosable={!user?.passwordChangeRequired && !changingPassword}
        keyboard={!user?.passwordChangeRequired && !changingPassword}
        okText='确认修改'
        cancelText={user?.passwordChangeRequired ? '退出登录' : '取消'}
        confirmLoading={changingPassword}
        onOk={() => void submitPassword()}
        cancelButtonProps={{ disabled: changingPassword }}
        onCancel={() => (changingPassword ? undefined : user?.passwordChangeRequired ? void exit() : setPasswordOpen(false))}
        destroyOnHidden
      >
        <Form form={passwordForm} labelCol={{ flex: '96px' }} labelWrap colon={false} requiredMark={false}>
          <Form.Item name='oldPassword' label='原密码' rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password autoComplete='current-password' />
          </Form.Item>
          <Form.Item
            name='newPassword'
            label='新密码'
            rules={[
              { required: true, message: '请输入新密码' },
              { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE },
            ]}
          >
            <Input.Password autoComplete='new-password' />
          </Form.Item>
          <Form.Item
            name='confirmPassword'
            label='确认新密码'
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete='new-password' />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}
