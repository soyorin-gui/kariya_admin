import { getTransaction, getTransactionMessageFields, getTransactionVersionDetail, getTransactionVersions } from '../../transaction/api';
import type { Transaction, TransactionMessageField, TransactionVersionMessageField } from '../../transaction/types';

export type DefinitionBindingType = 'DRAFT' | 'VERSION';

export interface DefinitionTarget {
  key: string;
  bindingType: DefinitionBindingType;
  transactionVersionId?: number;
  label: string;
}

export interface LoadedDefinition {
  transaction: Transaction;
  fields: TransactionMessageField[];
}

export async function getDefinitionTargets(transactionId: number): Promise<DefinitionTarget[]> {
  const versions = await getTransactionVersions(transactionId);
  return [
    { key: 'DRAFT', bindingType: 'DRAFT', label: '当前开发配置' },
    ...versions.map((version) => ({
      key: `VERSION:${version.id}`,
      bindingType: 'VERSION' as const,
      transactionVersionId: version.id,
      label: `V${version.versionNo}${version.activeFlag === 1 ? '（当前投产）' : '（历史）'}`,
    })),
  ];
}

export async function loadDefinition(transactionId: number, target: DefinitionTarget): Promise<LoadedDefinition> {
  if (target.bindingType === 'DRAFT') {
    const [transaction, fields] = await Promise.all([
      getTransaction(transactionId),
      getTransactionMessageFields(transactionId, 'REQUEST'),
    ]);
    return { transaction, fields };
  }
  const detail = await getTransactionVersionDetail(target.transactionVersionId!);
  const transaction = { ...detail.version, id: detail.version.transactionId, status: detail.version.transactionStatus, updatedTime: detail.version.createdTime } as Transaction;
  const fields = detail.messageFields.filter((field) => field.messageSide === 'REQUEST').map((field) => versionField(transactionId, field));
  return { transaction, fields };
}

function versionField(transactionId: number, field: TransactionVersionMessageField): TransactionMessageField {
  return {
    id: field.sourceFieldId,
    transactionId,
    messageSide: field.messageSide,
    parentId: field.parentSourceFieldId,
    nodeType: field.nodeType,
    fieldNameCn: field.fieldNameCn,
    fieldNameEn: field.fieldNameEn,
    dataType: field.dataType,
    dataLength: field.dataLength,
    requiredFlag: field.requiredFlag,
    description: field.description,
    sortOrder: field.sortOrder,
  };
}
