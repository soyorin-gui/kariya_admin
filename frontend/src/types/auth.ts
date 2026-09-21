export interface LoginRequest {
  username: string;
  password: string;
  rememberMe: boolean;
}
export interface LoginResult {
  accessToken: string;
  user: CurrentUser;
}
export interface AuthProfile {
  user: CurrentUser;
  permissions: string[];
  menus: MenuRoute[];
}
export interface CurrentUser {
  id: number;
  username: string;
  realName: string;
}
export interface MenuRoute {
  id: number;
  parentId: number;
  menuName: string;
  menuType: 'DIR' | 'MENU' | 'BUTTON';
  routePath?: string;
  component?: string;
  icon?: string;
  permissionCode?: string;
}
