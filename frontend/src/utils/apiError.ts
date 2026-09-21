import type { AxiosError } from 'axios';
import type { Result } from '../types/common';

export function getApiErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  const response = (error as AxiosError<Result<unknown>>).response;
  return response?.data?.message || fallback;
}
