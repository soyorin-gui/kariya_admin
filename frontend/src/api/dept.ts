import request from '../utils/request';
import type { Result } from '../types/common';
import type { Dept, DeptFormOptions, DeptRequest } from '../types/dept';

export const getDepts = () => request.get<Result<Dept[]>>('/system/depts').then((response) => response.data.data);
export const getDeptFormOptions = () => request.get<Result<DeptFormOptions>>('/system/depts/form-options').then((response) => response.data.data);
export const createDept = (data: DeptRequest) => request.post<Result<Dept>>('/system/depts', data).then((response) => response.data);
export const updateDept = (id: number, data: DeptRequest) => request.put<Result<Dept>>(`/system/depts/${id}`, data).then((response) => response.data);
export const deleteDept = (id: number) => request.delete(`/system/depts/${id}`);
