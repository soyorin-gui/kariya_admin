import request from '../utils/request';
import type { PageResult, Result } from '../types/common';

export type ApprovalScope = 'pending' | 'processed' | 'mine';

export interface ApprovalTask {
  requestId: number;
  businessType: 'DEPARTMENT_CHANGE';
  requesterName: string;
  fromDeptName: string;
  targetDeptName: string;
  reason: string;
  requestStatus: string;
  currentStep: number;
  stepType: 'SOURCE' | 'TARGET';
  stepStatus: string;
  assignedUserName?: string;
  decidedByName?: string;
  decisionReason?: string;
  createdTime: string;
  decidedTime?: string;
}

export const getApprovalTasks = (scope: ApprovalScope, pageNum = 1, pageSize = 10) =>
  request
    .get<Result<PageResult<ApprovalTask>>>('/account/approvals', { params: { scope, pageNum, pageSize } })
    .then((response) => response.data.data);

export const getPendingApprovalCount = () =>
  request.get<Result<{ count: number }>>('/account/approvals/pending-count').then((response) => response.data.data.count);
