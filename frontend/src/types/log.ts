/** 日志结果取值，与后端 org.kariya.system.log.LogResult 一致。 */
export type LogResult = 'SUCCESS' | 'FAILURE' | 'LOCKED';

/** 登录日志，对应后端 LoginLogEntity。 */
export interface LoginLog {
  id: number;
  username: string;
  userId: number | null;
  loginIp: string | null;
  userAgent: string | null;
  result: LogResult;
  message: string | null;
  loginTime: string;
}

/** 操作日志，对应后端 OperationLogEntity。 */
export interface OperationLog {
  id: number;
  userId: number | null;
  username: string | null;
  module: string;
  action: string;
  requestIp: string | null;
  result: Exclude<LogResult, 'LOCKED'>;
  durationMs: number | null;
  createdTime: string;
}

/**
 * 时间范围参数统一用 ISO-8601（YYYY-MM-DDTHH:mm:ss）字符串。
 * 后端用 @DateTimeFormat(iso = DATE_TIME) 绑定 LocalDateTime，格式写错会直接 400 而不是被静默忽略。
 */
export interface LogQuery {
  pageNum: number;
  pageSize: number;
  beginTime?: string;
  endTime?: string;
}

export interface LoginLogQuery extends LogQuery {
  username?: string;
  result?: LogResult;
}

export interface OperationLogQuery extends LogQuery {
  keyword?: string;
  module?: string;
  result?: LogResult;
}
