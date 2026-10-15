import request from '../../../../utils/request';
import type { PageResult, Result } from '../../../../types/common';
import type { FlowEnvironment, FlowFilterOptions, TransactionFlowQuery, TransactionFlowRecord } from './types';

export const getFlowEnvironments = () =>
  request.get<Result<FlowEnvironment[]>>('/intranet/operation/flows/environments').then((response) => response.data.data);

export const getFlowFilterOptions = (environmentId: string) =>
  request.get<Result<FlowFilterOptions>>('/intranet/operation/flows/options', { params: { environmentId } }).then((response) => response.data.data);

export const queryTransactionFlows = (data: TransactionFlowQuery) =>
  request.post<Result<PageResult<TransactionFlowRecord>>>('/intranet/operation/flows/query', data).then((response) => response.data.data);
