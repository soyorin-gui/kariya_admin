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
export interface UserFormOptions { departments: SelectOption[]; roles: SelectOption[] }
export interface SelectOption { value: number; label: string }
