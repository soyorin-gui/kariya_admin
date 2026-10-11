import request from '../../../utils/request';
import type { PageResult, Result } from '../../../types/common';
import type { EsField, EsFieldRequest, EsIndex, EsIndexRequest, HbaseField, HbaseFieldRequest, HbaseTable, HbaseTableRequest } from './types';

export const getHbaseTables = (params: { pageNum: number; pageSize: number; keyword?: string }) => request.get<Result<PageResult<HbaseTable>>>('/intranet/data-assets/hbase-tables', { params }).then(r => r.data.data);
export const getHbaseOptions = () => request.get<Result<HbaseTable[]>>('/intranet/data-assets/hbase-tables/options').then(r => r.data.data);
export const getHbaseTable = (id: number) => request.get<Result<HbaseTable>>(`/intranet/data-assets/hbase-tables/${id}`).then(r => r.data.data);
export const createHbaseTable = (data: HbaseTableRequest) => request.post<Result<HbaseTable>>('/intranet/data-assets/hbase-tables', data).then(r => r.data);
export const updateHbaseTable = (id: number, data: HbaseTableRequest) => request.put<Result<HbaseTable>>(`/intranet/data-assets/hbase-tables/${id}`, data).then(r => r.data);
export const deleteHbaseTable = (id: number) => request.delete(`/intranet/data-assets/hbase-tables/${id}`);
export const getHbaseFields = (tableId: number) => request.get<Result<HbaseField[]>>(`/intranet/data-assets/hbase-tables/${tableId}/fields`).then(r => r.data.data);
export const createHbaseField = (tableId: number, data: HbaseFieldRequest) => request.post<Result<HbaseField>>(`/intranet/data-assets/hbase-tables/${tableId}/fields`, data).then(r => r.data);
export const updateHbaseField = (tableId: number, id: number, data: HbaseFieldRequest) => request.put<Result<HbaseField>>(`/intranet/data-assets/hbase-tables/${tableId}/fields/${id}`, data).then(r => r.data);
export const deleteHbaseField = (tableId: number, id: number) => request.delete(`/intranet/data-assets/hbase-tables/${tableId}/fields/${id}`);

export const getEsIndexes = (params: { pageNum: number; pageSize: number; keyword?: string }) => request.get<Result<PageResult<EsIndex>>>('/intranet/data-assets/es-indexes', { params }).then(r => r.data.data);
export const getEsOptions = () => request.get<Result<EsIndex[]>>('/intranet/data-assets/es-indexes/options').then(r => r.data.data);
export const getEsIndex = (id: number) => request.get<Result<EsIndex>>(`/intranet/data-assets/es-indexes/${id}`).then(r => r.data.data);
export const createEsIndex = (data: EsIndexRequest) => request.post<Result<EsIndex>>('/intranet/data-assets/es-indexes', data).then(r => r.data);
export const updateEsIndex = (id: number, data: EsIndexRequest) => request.put<Result<EsIndex>>(`/intranet/data-assets/es-indexes/${id}`, data).then(r => r.data);
export const deleteEsIndex = (id: number) => request.delete(`/intranet/data-assets/es-indexes/${id}`);
export const getEsFields = (indexId: number) => request.get<Result<EsField[]>>(`/intranet/data-assets/es-indexes/${indexId}/fields`).then(r => r.data.data);
export const createEsField = (indexId: number, data: EsFieldRequest) => request.post<Result<EsField>>(`/intranet/data-assets/es-indexes/${indexId}/fields`, data).then(r => r.data);
export const updateEsField = (indexId: number, id: number, data: EsFieldRequest) => request.put<Result<EsField>>(`/intranet/data-assets/es-indexes/${indexId}/fields/${id}`, data).then(r => r.data);
export const deleteEsField = (indexId: number, id: number) => request.delete(`/intranet/data-assets/es-indexes/${indexId}/fields/${id}`);
