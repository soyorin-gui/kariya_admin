import { useMemo, type ReactNode } from 'react';
import type { ItemType } from 'antd/es/menu/interface';
import { Avatar, Breadcrumb, Button, Dropdown, Input, Layout, Menu, Space, Tooltip } from 'antd';
import { BellOutlined, DashboardOutlined, HomeOutlined, LogoutOutlined, MenuOutlined, SearchOutlined, SettingOutlined, UserOutlined, ApartmentOutlined, SafetyOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '../components/common/Logo';
import { AiAssistant } from '../components/ai/AiAssistant';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { clearSession } from '../store/authSlice';
import { logout } from '../api/auth';
import type { MenuRoute } from '../types/auth';
import { menuBreadcrumb } from '../router/menu';
const { Sider, Header, Content } = Layout;
const iconMap: Record<string, ReactNode> = {
  DashboardOutlined: <DashboardOutlined />,
  SettingOutlined: <SettingOutlined />,
  UserOutlined: <UserOutlined />,
  SafetyOutlined: <SafetyOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  MenuOutlined: <MenuOutlined />,
};
const tree = [
  { key: '/home', icon: <DashboardOutlined />, label: <Link to='/home'>首页</Link> },
  {
    key: 'system',
    icon: <SettingOutlined />,
    label: '系统管理',
    children: [
      {
        key: '/system/user',
        icon: <UserOutlined />,
        label: <Link to='/system/user'>用户管理</Link>,
      },
      {
        key: '/system/role',
        icon: <SafetyOutlined />,
        label: <Link to='/system/role'>角色管理</Link>,
      },
      {
        key: '/system/dept',
        icon: <ApartmentOutlined />,
        label: <Link to='/system/dept'>部门管理</Link>,
      },
      {
        key: '/system/menu',
        icon: <MenuOutlined />,
        label: <Link to='/system/menu'>菜单管理</Link>,
      },
    ],
  },
];
function buildMenu(routes: MenuRoute[]): ItemType[] {
  const visible = routes.filter((m) => m.menuType !== 'BUTTON');
  const children = (parentId: number): ItemType[] =>
    visible
      .filter((m) => m.parentId === parentId)
      .map((m) => {
        const nestedItems = children(m.id);
        return {
          key: m.routePath ?? `menu-${m.id}`,
          icon: iconMap[m.icon ?? ''] ?? <MenuOutlined />,
          label: m.menuType === 'DIR' ? m.menuName : <Link to={m.routePath ?? '/home'}>{m.menuName}</Link>,
          children: nestedItems.length ? nestedItems : undefined,
        };
      });
  return children(0);
}
export function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const menus = useAppSelector((s) => s.auth.menus);
  const items = useMemo(() => (menus.length ? buildMenu(menus) : tree), [menus]);
  const exit = async () => {
    try {
      await logout();
    } finally {
      dispatch(clearSession());
      navigate('/login');
    }
  };
  const crumb = menuBreadcrumb(location.pathname, menus).map((title) => ({ title }));
  return (
    <Layout className='app-shell'>
      <Sider width={224} theme='light' className='app-sider'>
        <Logo />
        <Menu mode='inline' selectedKeys={[location.pathname]} defaultOpenKeys={['/system', 'system']} items={items} />
        <div className='sider-foot'>
          高效 · 安全 · 专业
          <br />
          <span>
            Kariya Admin Console
            <br />
            v1.0.0
          </span>
        </div>
      </Sider>
      <Layout className='app-main-layout'>
        <Header className='app-header'>
          <div className='header-navigation'>
            <Link to='/home' className='header-home'><HomeOutlined /> 首页</Link>
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
                items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: exit }],
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
    </Layout>
  );
}
