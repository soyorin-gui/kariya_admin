import request from '../utils/request';
import type { Result, PageResult } from '../types/common';
import type { LoginLog, LoginLogQuery } from '../types/log';

export const getLoginLogs = (params: LoginLogQuery) => request.get<Result<PageResult<LoginLog>>>('/system/login-logs', { params }).then((r) => r.data.data);

/**
 * 批量删除。用 query 参数而不是请求体传 ids：DELETE 带 body 在部分代理/网关下会被丢掉，
 * 而后端是 @RequestParam List<Long>，需要拼成 ids=1,2,3 的逗号分隔形式。
 */
export const deleteLoginLogs = (ids: number[]) => request.delete<Result<void>>('/system/login-logs', { params: { ids: ids.join(',') } }).then((r) => r.data);
