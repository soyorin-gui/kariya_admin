export type MenuType = 'DIR' | 'MENU' | 'BUTTON';

export interface SystemMenu {
  id: number;
  parentId: number;
  menuName: string;
  menuType: MenuType;
  routeName?: string;
  routePath?: string;
  component?: string;
  permissionCode?: string;
  icon?: string;
  sortOrder: number;
  visible: number;
  status: number;
  keepAlive: number;
  builtin: number;
  createdTime?: string;
}

export interface MenuRequest {
  parentId?: number;
  menuName: string;
  menuType: MenuType;
  routeName?: string;
  routePath?: string;
  component?: string;
  permissionCode?: string;
  icon?: string;
  sortOrder: number;
  visible: number;
  status: number;
  keepAlive: number;
}
