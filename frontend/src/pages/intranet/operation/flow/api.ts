import request from '../../../../utils/request';
import type { PageResult, Result } from '../../../../types/common';
import type { FlowEnvironment, FlowFilterOptions, FlowLogContext, FlowLogFile, TransactionFlowQuery, TransactionFlowRecord } from './types';

export const getFlowEnvironments = () =>
  request.get<Result<FlowEnvironment[]>>('/intranet/operation/flows/environments').then((response) => response.data.data);

export const getFlowFilterOptions = (environmentId: string) =>
  request.get<Result<FlowFilterOptions>>('/intranet/operation/flows/options', { params: { environmentId } }).then((response) => response.data.data);

export const queryTransactionFlows = (data: TransactionFlowQuery) =>
  request.post<Result<PageResult<TransactionFlowRecord>>>('/intranet/operation/flows/query', data).then((response) => response.data.data);

export const getFlowLogContext = (environmentId: string, flow: TransactionFlowRecord) =>
  request.post<Result<FlowLogContext>>('/intranet/operation/logs/context', { environmentId, flow }).then((response) => response.data.data);

export const downloadFlowLog = (environmentId: string, file: FlowLogFile) =>
  request.get(`/intranet/operation/logs/files/${file.fileType}/${file.fileId}/download`, { params: { environmentId }, responseType: 'blob' }).then((response) => response.data as Blob);
