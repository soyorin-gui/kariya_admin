export interface MockTransactionInvocationRequest {
  transactionId: number;
  targetAddress: string;
  soapVersion: '1.1' | '1.2';
}

export interface MockTransactionInvocationResult {
  transactionId: number;
  transactionCode: string;
  transactionName: string;
  targetAddress: string;
  success: boolean;
  responseStatus: 'Y' | 'N' | 'E';
  responseCode: string;
  responseMessage: string;
  globalFlowId: string;
  elapsedTime: number;
  requestXml: string;
  responseXml: string;
}
