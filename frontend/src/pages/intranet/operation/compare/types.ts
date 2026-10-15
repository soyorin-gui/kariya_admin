export interface ComparisonEnvironment {
  id: string;
  name: string;
}

export interface MockResponseComparisonRequest {
  transactionId: number;
  environmentAId: string;
  environmentBId: string;
  soapVersion: '1.1' | '1.2';
  bindingTypeA: 'DRAFT' | 'VERSION';
  transactionVersionIdA?: number;
  testCaseIdA?: number;
  bindingTypeB: 'DRAFT' | 'VERSION';
  transactionVersionIdB?: number;
  testCaseIdB?: number;
  requestXmlA: string;
  requestXmlB: string;
}

export interface EnvironmentInvocationResult {
  environmentId: string;
  environmentName: string;
  success: boolean;
  responseStatus: string;
  responseCode: string;
  globalFlowId: string;
  elapsedTime: number;
  requestXml: string;
  responseXml: string;
}

export type DifferenceType = 'SAME' | 'VALUE_DIFFERENT' | 'ONLY_A' | 'ONLY_B';

export interface ResponseFieldComparison {
  fieldPath: string;
  differenceType: DifferenceType;
  valueA?: string;
  valueB?: string;
}

export interface MockResponseComparison {
  transactionId: number;
  transactionCode: string;
  transactionName: string;
  environmentA: EnvironmentInvocationResult;
  environmentB: EnvironmentInvocationResult;
  fieldComparisons: ResponseFieldComparison[];
}
