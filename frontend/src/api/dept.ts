import request from '../utils/request';
import type { Result } from '../types/common';
import type { Dept, DeptFormOptions, DeptRequest } from '../types/dept';

export const getDepts = () => request.get<Result<Dept[]>>('/system/depts').then((response) => response.data.data);
export const getDept = (id: number) => request.get<Result<Dept>>(`/system/depts/${id}`).then((response) => response.data.data);
export const getDeptFormOptions = (operation: 'add' | 'update' = 'add') => request.get<Result<DeptFormOptions>>('/system/depts/form-options', { params: { operation } }).then((response) => response.data.data);
export const createDept = (data: DeptRequest) => request.post<Result<Dept>>('/system/depts', data).then((response) => response.data);
export const updateDept = (id: number, data: DeptRequest) => request.put<Result<Dept>>(`/system/depts/${id}`, data).then((response) => response.data);
export const deleteDept = (id: number) => request.delete(`/system/depts/${id}`);
