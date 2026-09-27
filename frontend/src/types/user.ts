export interface UserListItem {
  id: number;
  username: string;
  realName: string;
  phone: string;
  deptName: string;
  roleNames: string;
  status: number;
  createdTime: string;
  manageable: boolean;
  deletable: boolean;
  resettable: boolean;
}

/** 编辑弹窗按 ID 获取的最新详情，不混入用户列表响应。 */
export interface User {
  id: number;
  username: string;
  realName: string;
  phone: string;
  email?: string;
  /**
   * 所属部门。**可以为 null**：自助注册与外部身份开户创建的账号默认没有部门
   * （sys_user.dept_id 本身允许为 NULL）。界面必须把"没有部门"和"原部门被停用"
   * 当成两回事，否则会拼出一个「-（当前所属部门，已停用）」的假节点。
   */
  deptId: number | null;
  deptName: string;
  roleIds: number[];
  roleNames: string;
  status: number;
  createdTime: string;
  manageable: boolean;
  deletable: boolean;
  resettable: boolean;
}
export interface UserRequest {
  username: string;
  realName: string;
  phone: string;
  email?: string;
  deptId: number;
  roleIds: number[];
  status: number;
}
export interface UserCreateRequest extends UserRequest {
  password: string;
  confirmPassword: string;
}
export interface UserFormOptions {
  departments: DepartmentOption[];
  roles: SelectOption[];
}
export interface SelectOption {
  value: number;
  label: string;
  disabled?: boolean;
}
export interface DepartmentOption extends SelectOption {
  parentId?: number;
}
/** 用户名可用性校验结果，见后端 UsernameAvailability。 */
export interface UsernameAvailability {
  available: boolean;
  message?: string | null;
}
