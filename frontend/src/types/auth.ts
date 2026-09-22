export interface LoginRequest {
  username: string;
  password: string;
  rememberMe: boolean;
}
export interface LoginResult {
  accessToken: string;
  user: Pick<CurrentUser, 'id' | 'username' | 'realName' | 'passwordChangeRequired'>;
}
export interface AuthProfile {
  user: CurrentUser;
  permissions: string[];
  /** 当前用户有权访问的菜单树（含祖先节点与已授权的按钮）。这是前端一切路由与菜单渲染的唯一依据。 */
  menus: MenuRoute[];
  /**
   * 全站已配置的页面路由目录，只有 routePath + 名称，不含任何权限信息。
   * 唯一用途是让前端区分「这个路径存在但我没权限（403）」和「这个路径根本不存在（404）」。
   * 不算新增泄露：在传统的静态路由方案里，整张路由表本来就随 JS 包公开给所有人。
   */
  routes: MenuRouteSummary[];
}
export interface MenuRouteSummary {
  routePath: string;
  menuName: string;
}
export interface CurrentUser {
  id: number;
  username: string;
  realName: string;
  passwordChangeRequired: boolean;
  superAdmin: boolean;
}
/**
 * 后端 sys_menu 的记录，字段与 MenuEntity 一一对应。
 * visible / sortOrder / status 必须带上：侧边栏要靠它们做「隐藏」和「排序」，
 * 之前这三个字段没定义，导致菜单管理里的「隐藏」开关存了值却完全没生效。
 */
export interface MenuRoute {
  id: number;
  parentId: number;
  menuName: string;
  menuType: 'DIR' | 'MENU' | 'BUTTON';
  routeName?: string;
  routePath?: string;
  component?: string;
  icon?: string;
  permissionCode?: string;
  sortOrder?: number;
  /** 1 = 显示，0 = 隐藏。隐藏的菜单不出现在侧边栏，但仍可通过 URL 直接访问（例如详情页）。 */
  visible?: number;
  status?: number;
  keepAlive?: number;
}
