export interface AuditFields { createdBy?: number; createdTime?: string; updatedBy?: number; updatedTime?: string }
export interface HbaseTable extends AuditFields { id: number; marketName: string; tableName: string; description?: string; rowkeyRule?: string; lockKey?: string; dataVolume?: string; dataDepth?: string }
export interface HbaseTableRequest { marketName: string; tableName: string; description?: string; rowkeyRule?: string; lockKey?: string; dataVolume?: string; dataDepth?: string }
export interface HbaseField extends AuditFields { id: number; hbaseTableId: number; columnFamily: string; fieldName: string; dataType?: string; description?: string; sortOrder: number }
export interface HbaseFieldRequest { columnFamily: string; fieldName: string; dataType?: string; description?: string; sortOrder: number }
export interface EsIndex extends AuditFields { id: number; indexName: string; indexPattern: string; partitionRule?: string; hbaseTableId: number; hbaseMarketName: string; hbaseTableName: string; description?: string }
export interface EsIndexRequest { indexName: string; indexPattern: string; partitionRule?: string; hbaseTableId: number; description?: string }
export interface EsField extends AuditFields { id: number; esIndexId: number; fieldName: string; dataType: string; searchableFlag: number; sortableFlag: number; description?: string; sortOrder: number }
export interface EsFieldRequest { fieldName: string; dataType: string; searchableFlag: number; sortableFlag: number; description?: string; sortOrder: number }
export interface TransactionDataResources { hbaseTableIds: number[]; esIndexIds: number[] }
