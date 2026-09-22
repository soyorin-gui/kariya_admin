import type { ReactNode } from 'react';
import {
  ApartmentOutlined,
  DashboardOutlined,
  FileSearchOutlined,
  FormOutlined,
  HistoryOutlined,
  HomeOutlined,
  MenuOutlined,
  ProfileOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons';

/**
 * 菜单图标注册表。
 *
 * 这里刻意是一份**白名单**，而不是 `import * as Icons from '@ant-design/icons'` 那种写法：
 * 后者会把上千个图标全部打进产物（体积增加数百 KB），而一个后台能用的图标本来就该是有限集合。
 *
 * 菜单管理表单的图标下拉直接读这份清单，所以不会再出现「填了个图标名却渲染成默认图标」的情况。
 * 需要新图标时：在上面的 import 里加一个，并在 MENU_ICONS 里登记一行。
 */
export const MENU_ICONS: Record<string, ReactNode> = {
  DashboardOutlined: <DashboardOutlined />,
  HomeOutlined: <HomeOutlined />,
  SettingOutlined: <SettingOutlined />,
  UserOutlined: <UserOutlined />,
  TeamOutlined: <TeamOutlined />,
  SafetyOutlined: <SafetyOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  MenuOutlined: <MenuOutlined />,
  ProfileOutlined: <ProfileOutlined />,
  FileSearchOutlined: <FileSearchOutlined />,
  HistoryOutlined: <HistoryOutlined />,
  FormOutlined: <FormOutlined />,
  ToolOutlined: <ToolOutlined />,
};

/** 供菜单管理表单的图标下拉使用。 */
export const MENU_ICON_OPTIONS = Object.entries(MENU_ICONS).map(([name, node]) => ({ value: name, label: <span className='menu-icon-option'>{node}{name}</span> }));

/** 图标名 → 图标节点；未登记的名字回退成默认图标，不会渲染成空白。 */
export function resolveMenuIcon(name?: string | null): ReactNode {
  return (name && MENU_ICONS[name]) || <MenuOutlined />;
}
