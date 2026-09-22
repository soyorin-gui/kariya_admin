import request from '../utils/request';
import type { Result, PageResult } from '../types/common';
import type { OperationLog, OperationLogQuery } from '../types/log';

export const getOperationLogs = (params: OperationLogQuery) => request.get<Result<PageResult<OperationLog>>>('/system/operation-logs', { params }).then((r) => r.data.data);

/** 批量删除，说明同 loginLog.ts。 */
export const deleteOperationLogs = (ids: number[]) => request.delete<Result<void>>('/system/operation-logs', { params: { ids: ids.join(',') } }).then((r) => r.data);
