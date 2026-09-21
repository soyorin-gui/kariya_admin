export interface Result<T> {
  code: number;
  message: string;
  data: T;
  requestId?: string;
}
export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}
