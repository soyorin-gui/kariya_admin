import request from '../utils/request';
import type { Result } from '../types/common';

export interface DepartmentChangeStep {
  id: number;
  order: number;
  type: 'SOURCE' | 'TARGET';
  deptId?: number;
  deptName?: string;
  assignedUserId?: number;
  assignedUserName?: string;
  status: 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
  decidedBy?: number;
  decidedByName?: string;
  decisionReason?: string;
  decidedTime?: string;
}
export interface DepartmentChangeDetail {
  id: number;
  requesterId: number;
  requesterName: string;
  fromDeptId?: number;
  fromDeptName: string;
  targetDeptId: number;
  targetDeptName: string;
  reason: string;
  status: 'PENDING_SOURCE' | 'PENDING_TARGET' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  currentStep: number;
  createdTime: string;
  finishedTime?: string;
  canApprove: boolean;
  canCancel: boolean;
  steps: DepartmentChangeStep[];
}
export interface DepartmentProfile {
  currentDeptId?: number;
  currentDeptName?: string;
  activeRequest?: DepartmentChangeDetail;
}

export const getDepartmentOptions = () => request.get<Result<Array<{ id: number; name: string }>>>('/account/department-change/options').then((r) => r.data.data);
export const getDepartmentProfile = () => request.get<Result<DepartmentProfile>>('/account/department-change/profile').then((r) => r.data.data);
export const submitDepartmentChange = (payload: { targetDeptId: number; reason: string }) => request.post<Result<DepartmentChangeDetail>>('/account/department-change', payload).then((r) => r.data);
export const getDepartmentChange = (id: number) => request.get<Result<DepartmentChangeDetail>>(`/account/department-change/${id}`).then((r) => r.data.data);
export const approveDepartmentChange = (id: number) => request.post<Result<DepartmentChangeDetail>>(`/account/department-change/${id}/approve`).then((r) => r.data);
export const rejectDepartmentChange = (id: number, reason: string) => request.post<Result<DepartmentChangeDetail>>(`/account/department-change/${id}/reject`, { reason }).then((r) => r.data);
export const cancelDepartmentChange = (id: number) => request.post<Result<DepartmentChangeDetail>>(`/account/department-change/${id}/cancel`).then((r) => r.data);
