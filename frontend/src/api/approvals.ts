import request from '../utils/request';
import type { PageResult, Result } from '../types/common';

export type ApprovalScope = 'pending' | 'processed' | 'mine';

export interface ApprovalTask {
  approvalInstanceId: number;
  businessId: number;
  businessType: string;
  businessTitle: string;
  businessSummary?: string;
  requesterName: string;
  requestStatus: string;
  currentStep: number;
  approvalRound: number;
  stepType: string;
  stepName: string;
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
