import request from '../../../../utils/request';
import type { Result } from '../../../../types/common';
import type { DefinitionBindingType } from './definitions';

export interface OperationTestCase {
  id: number;
  transactionId: number;
  bindingType: DefinitionBindingType;
  transactionVersionId?: number;
  caseName: string;
  description?: string;
  requestPayload: string;
  createdBy?: number;
  createdTime: string;
  updatedBy?: number;
  updatedTime: string;
}

export interface OperationTestCaseInput {
  transactionId: number;
  bindingType: DefinitionBindingType;
  transactionVersionId?: number;
  caseName: string;
  description?: string;
  requestPayload: string;
}

export const getOperationTestCases = (params: { transactionId: number; bindingType: DefinitionBindingType; transactionVersionId?: number }) =>
  request.get<Result<OperationTestCase[]>>('/intranet/operation/test-cases', { params }).then((response) => response.data.data);

export const createOperationTestCase = (data: OperationTestCaseInput) =>
  request.post<Result<OperationTestCase>>('/intranet/operation/test-cases', data).then((response) => response.data.data);

export const updateOperationTestCase = (id: number, data: OperationTestCaseInput) =>
  request.put<Result<OperationTestCase>>(`/intranet/operation/test-cases/${id}`, data).then((response) => response.data.data);

export const deleteOperationTestCase = (id: number) => request.delete(`/intranet/operation/test-cases/${id}`);

export const copyOperationTestCase = (id: number, data: { bindingType: DefinitionBindingType; transactionVersionId?: number; caseName?: string }) =>
  request.post<Result<OperationTestCase>>(`/intranet/operation/test-cases/${id}/copies`, data).then((response) => response.data.data);
