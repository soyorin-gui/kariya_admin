import request from '../../../../utils/request';
import type { Result } from '../../../../types/common';
import type { MockTransactionInvocationRequest, MockTransactionInvocationResult } from './types';

export const invokeMockTransaction = (data: MockTransactionInvocationRequest) =>
  request.post<Result<MockTransactionInvocationResult>>('/intranet/operation/invocations/mock', data).then((response) => response.data.data);
