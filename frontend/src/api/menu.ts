import request from '../utils/request';
import type { Result } from '../types/common';
import type { MenuRequest, SystemMenu } from '../types/menu';

export const getMenus = () => request.get<Result<SystemMenu[]>>('/system/menus').then((response) => response.data.data);
export const createMenu = (data: MenuRequest) => request.post<Result<SystemMenu>>('/system/menus', data).then((response) => response.data);
export const updateMenu = (id: number, data: MenuRequest) => request.put<Result<SystemMenu>>(`/system/menus/${id}`, data).then((response) => response.data);
export const deleteMenu = (id: number) => request.delete(`/system/menus/${id}`);
