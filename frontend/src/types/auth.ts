export interface LoginRequest {
  username: string;
  password: string;
  rememberMe: boolean;
}
export interface LoginResult {
  accessToken: string;
  user: Pick<CurrentUser, 'id' | 'username' | 'realName' | 'passwordChangeRequired'>;
}
export interface RegistrationRequest {
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  password: string;
  confirmPassword: string;
  rememberMe: boolean;
  /** 图形验证码的题目 id；验证码功能关闭时后端返回 null，这里不传。 */
  captchaId?: string;
  captchaCode?: string;
}
/**
 * 图形验证码题目。
 * enabled=false 时 captchaId / image 都是 null，前端必须把输入框整个隐藏
 * ——否则会出现一个永远不可能通过的必填项。
 */
export interface CaptchaChallenge {
  enabled: boolean;
  captchaId: string | null;
  image: string | null;
}
export interface ExternalProvider {
  key: string;
  displayName: string;
  icon: string;
  protocol: string;
  enabled: boolean;
}
export interface OnboardingProfile {
  providerKey: string;
  displayName?: string;
  email?: string;
  employeeNo?: string;
}
export interface AuthProfile {
  principalType: 'MEMBER' | 'ONBOARDING';
  user?: CurrentUser;
  onboarding?: OnboardingProfile;
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
  hasPassword: boolean;
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
