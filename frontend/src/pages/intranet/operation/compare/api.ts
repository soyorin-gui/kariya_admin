import request from '../../../../utils/request';
import type { Result } from '../../../../types/common';
import type { ComparisonEnvironment, MockResponseComparison, MockResponseComparisonRequest } from './types';

export const getComparisonEnvironments = () =>
  request.get<Result<ComparisonEnvironment[]>>('/intranet/operation/comparisons/environments').then((response) => response.data.data);

export const compareMockResponses = (data: MockResponseComparisonRequest) =>
  request.post<Result<MockResponseComparison>>('/intranet/operation/comparisons/mock', data).then((response) => response.data.data);
