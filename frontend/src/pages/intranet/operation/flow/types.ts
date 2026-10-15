export interface FlowEnvironment {
  id: string;
  name: string;
}

export interface FlowFilterOptions {
  responseStatuses: string[];
  responseCodes: string[];
}

export interface TransactionFlowRecord {
  callerId: string;
  serviceAddress: string;
  transactionCode: string;
  startTime: string;
  endTime: string;
  elapsedTime: number;
  responseStatus: 'Y' | 'N' | 'E';
  responseCode: string;
  queryDate: string;
  globalFlowId: string;
}

export interface TransactionFlowQuery {
  environmentId: string;
  queryDate: string;
  callerId?: string;
  serviceAddress?: string;
  transactionCode?: string;
  startTime?: string;
  endTime?: string;
  responseStatus?: string;
  responseCode?: string;
  globalFlowId?: string;
  pageNum: number;
  pageSize: number;
}
