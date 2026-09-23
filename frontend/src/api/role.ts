import request from '../utils/request';
import type { Result } from '../types/common';
import type { Role, RoleRequest } from '../types/role';
import type { SystemMenu } from '../types/menu';

export const getRoles = (keyword?: string) => request.get<Result<Role[]>>('/system/roles', { params: { keyword } }).then((response) => response.data.data);
export const createRole = (data: RoleRequest) => request.post<Result<Role>>('/system/roles', data).then((response) => response.data);
export const updateRole = (id: number, data: RoleRequest) => request.put<Result<Role>>(`/system/roles/${id}`, data).then((response) => response.data);
export const deleteRole = (id: number) => request.delete(`/system/roles/${id}`);
export const getRoleMenuIds = (id: number) => request.get<Result<number[]>>(`/system/roles/${id}/menu-ids`).then((response) => response.data.data);
export const getGrantableMenus = () => request.get<Result<SystemMenu[]>>('/system/roles/grantable-menus').then((response) => response.data.data);
export const grantRoleMenus = (id: number, menuIds: number[]) => request.put<Result<void>>(`/system/roles/${id}/menu-ids`, menuIds).then((response) => response.data);
