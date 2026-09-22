export interface User {
  id: number;
  username: string;
  realName: string;
  phone: string;
  email?: string;
  deptId: number;
  deptName: string;
  roleIds: number[];
  roleNames: string;
  status: number;
  createdTime: string;
  manageable: boolean;
  deletable: boolean;
  resettable: boolean;
}
export interface UserCreated { user: User; temporaryPassword: string }
export interface UserRequest {
  username: string;
  realName: string;
  phone: string;
  email?: string;
  deptId: number;
  roleIds: number[];
  status: number;
}
export interface UserFormOptions { departments: SelectOption[]; roles: SelectOption[] }
export interface SelectOption { value: number; label: string }
/** 用户名可用性校验结果，见后端 UsernameAvailability。 */
export interface UsernameAvailability { available: boolean; message?: string | null }
