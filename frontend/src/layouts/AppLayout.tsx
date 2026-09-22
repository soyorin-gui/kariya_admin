import { useEffect, useMemo, useState } from 'react';
import type { ItemType } from 'antd/es/menu/interface';
import { App, Avatar, Breadcrumb, Button, Dropdown, Form, Input, Layout, Menu, Modal, Space, Tooltip } from 'antd';
import { BellOutlined, HomeOutlined, LockOutlined, LogoutOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '../components/common/Logo';
import { AiAssistant } from '../components/ai/AiAssistant';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { clearSession } from '../store/authSlice';
import { logout } from '../api/auth';
import { changeOwnPassword } from '../api/user';
import { getApiErrorMessage } from '../utils/apiError';
import type { MenuRoute } from '../types/auth';
import { ancestorMenuKeys, findMenuByPath, firstAvailablePath, menuBreadcrumb } from '../router/menu';
import { resolveMenuIcon } from '../router/iconRegistry';

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
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordForm] = Form.useForm<{ oldPassword: string; newPassword: string; confirmPassword: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const menus = useAppSelector((s) => s.auth.menus);
  const items = useMemo(() => buildMenu(menus), [menus]);
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
  const landing = useMemo(() => findMenuByPath(menus, HOME_ROUTE) ?? findMenuByPath(menus, firstAvailablePath(menus)), [menus]);
  const exit = async () => {
    try {
      await logout();
    } finally {
      dispatch(clearSession());
      navigate('/login');
    }
  };
  const submitPassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      setChangingPassword(true);
      await changeOwnPassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('密码已修改，请重新登录');
      dispatch(clearSession());
      navigate('/login', { replace: true });
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '修改密码失败'));
    } finally {
      setChangingPassword(false);
    }
  };
  const crumb = menuBreadcrumb(location.pathname, menus).map((title) => ({ title }));
  return (
    <Layout className='app-shell'>
      <Sider width={240} theme='light' className='app-sider'>
        <Logo />
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
            <Link to={landing?.routePath ?? HOME_ROUTE} className='header-home'>
              {/*
                图标必须和侧边栏同一项完全一致，所以两边都走 resolveMenuIcon，
                连"图标没配时回退成哪个图标"也保持一致（都回退到 MenuOutlined）。
                之前这里写的是 landing?.icon ? resolveMenuIcon(...) : <HomeOutlined />，
                一旦首页菜单的图标是空的，侧边栏回退成 MenuOutlined、顶栏却是 HomeOutlined，
                看起来就是"两个首页图标不一样"。
              */}
              {landing ? resolveMenuIcon(landing.icon) : <HomeOutlined />} 首页
            </Link>
            {crumb.length > 0 && <Breadcrumb items={crumb} />}
          </div>
          <Space size={20}>
            <Input className='quick-search' prefix={<SearchOutlined />} placeholder='搜索功能、文档或快捷操作...' />
            <Tooltip title='通知'>
              <Button type='text' shape='circle' icon={<BellOutlined />} />
            </Tooltip>
            <Tooltip title='系统设置'>
              <Button type='text' shape='circle' icon={<SettingOutlined />} />
            </Tooltip>
            <Dropdown
              menu={{
                items: [
                  { key: 'password', icon: <LockOutlined />, label: '修改密码', onClick: () => setPasswordOpen(true) },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: exit },
                ],
              }}
            >
              <Space className='user-menu'>
                <Avatar style={{ backgroundColor: 'var(--brand)' }}>{user?.username.slice(0, 1).toUpperCase()}</Avatar>
                <span>{user?.username ?? 'admin'}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content className='app-content'>
          <Outlet />
        </Content>
      </Layout>
      <AiAssistant />
      <Modal
        title={user?.passwordChangeRequired ? '首次登录，请修改密码' : '修改密码'}
        open={Boolean(user?.passwordChangeRequired || passwordOpen)}
        closable={!user?.passwordChangeRequired}
        maskClosable={!user?.passwordChangeRequired}
        okText='确认修改'
        cancelText={user?.passwordChangeRequired ? '退出登录' : '取消'}
        confirmLoading={changingPassword}
        onOk={() => void submitPassword()}
        onCancel={() => user?.passwordChangeRequired ? void exit() : setPasswordOpen(false)}
        destroyOnHidden
      >
        <Form form={passwordForm} layout='vertical'>
          <Form.Item name='oldPassword' label='原密码' rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password autoComplete='current-password' />
          </Form.Item>
          <Form.Item name='newPassword' label='新密码' rules={[{ required: true, min: 12, message: '新密码至少 12 位' }]}>
            <Input.Password autoComplete='new-password' />
          </Form.Item>
          <Form.Item name='confirmPassword' label='确认新密码' dependencies={['newPassword']} rules={[{ required: true, message: '请确认新密码' },
            ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}>
            <Input.Password autoComplete='new-password' />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}
