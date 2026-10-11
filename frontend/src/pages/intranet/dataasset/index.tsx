import { useRef, useState } from 'react';
import { App, Button, Drawer, Input, Popconfirm, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { Permission } from '../../../permission/Permission';
import { getApiErrorMessage } from '../../../utils/apiError';
import { deleteEsField, deleteEsIndex, deleteHbaseField, deleteHbaseTable, getEsFields, getEsIndexes, getHbaseFields, getHbaseTables } from './api';
import type { EsField, EsIndex, HbaseField, HbaseTable } from './types';
import { EsFieldDialog, EsIndexDialog, HbaseFieldDialog, HbaseTableDialog } from './AssetDialogs';
import './index.css';

type Search = { keyword: string };
function Text({ value }: { value?: string }) { return value ? <Tooltip title={value}><span className='dataasset-ellipsis'>{value}</span></Tooltip> : <>-</>; }

export default function DataAssetPage() {
  const { message } = App.useApp();
  const hbaseRef = useRef<SmartTableRef<Search>>(null); const esRef = useRef<SmartTableRef<Search>>(null);
  const [tab, setTab] = useState('hbase');
  const [hbaseEditing, setHbaseEditing] = useState<number | null | undefined>(); const [esEditing, setEsEditing] = useState<number | null | undefined>();
  const [fieldOwner, setFieldOwner] = useState<HbaseTable | EsIndex>(); const [fields, setFields] = useState<(HbaseField | EsField)[]>([]); const [fieldLoading, setFieldLoading] = useState(false); const [fieldEditing, setFieldEditing] = useState<HbaseField | EsField | null | undefined>();
  const ownerType = fieldOwner && 'marketName' in fieldOwner ? 'hbase' : 'es';
  const loadFields = async (owner = fieldOwner) => { if (!owner) return; setFieldLoading(true); try { setFields('marketName' in owner ? await getHbaseFields(owner.id) : await getEsFields(owner.id)); } catch (e) { message.error(getApiErrorMessage(e, '无法加载字段')); } finally { setFieldLoading(false); } };
  const openFields = (owner: HbaseTable | EsIndex) => { setFieldOwner(owner); setFields([]); void loadFields(owner); };
  const removeField = async (field: HbaseField | EsField) => { if (!fieldOwner) return; try { if (ownerType === 'hbase') await deleteHbaseField(fieldOwner.id, field.id); else await deleteEsField(fieldOwner.id, field.id); message.success('删除字段成功'); void loadFields(); } catch (e) { message.error(getApiErrorMessage(e)); } };

  const hbaseColumns: ColumnsType<HbaseTable> = [
    { title: '集市名', dataIndex: 'marketName', width: 110 }, { title: 'HBase 表名', dataIndex: 'tableName', width: 200 },
    { title: '数据量', dataIndex: 'dataVolume', width: 120, render: v => <Text value={v} /> }, { title: '数据深度', dataIndex: 'dataDepth', width: 120, render: v => <Text value={v} /> },
    { title: '锁标识', dataIndex: 'lockKey', width: 150, render: v => <Text value={v} /> }, { title: 'RowKey 规则', dataIndex: 'rowkeyRule', width: 220, render: v => <Text value={v} /> },
    { title: '更新时间', dataIndex: 'updatedTime', width: 170, render: v => v?.replace('T', ' ') },
    { title: '操作', fixed: 'right', width: 230, render: (_, row) => <Space><a onClick={() => openFields(row)}>字段</a><Permission code='intranet:dataasset:update'><a onClick={() => setHbaseEditing(row.id)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'><Popconfirm title='确定删除此 HBase 表？' description='存在 ES 或交易引用时不能删除。' onConfirm={() => void deleteHbaseTable(row.id).then(() => { message.success('删除成功'); hbaseRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm></Permission></Space> },
  ];
  const esColumns: ColumnsType<EsIndex> = [
    { title: '索引名称', dataIndex: 'indexName', width: 180 }, { title: '索引命名模式', dataIndex: 'indexPattern', width: 220, render: v => <Text value={v} /> },
    { title: '分片规则', dataIndex: 'partitionRule', width: 130, render: v => <Text value={v} /> }, { title: '对应 HBase 表', width: 200, render: (_, row) => `${row.hbaseMarketName}:${row.hbaseTableName}` },
    { title: '说明', dataIndex: 'description', width: 220, render: v => <Text value={v} /> }, { title: '更新时间', dataIndex: 'updatedTime', width: 170, render: v => v?.replace('T', ' ') },
    { title: '操作', fixed: 'right', width: 230, render: (_, row) => <Space><a onClick={() => openFields(row)}>字段</a><Permission code='intranet:dataasset:update'><a onClick={() => setEsEditing(row.id)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'><Popconfirm title='确定删除此 ES 索引？' description='存在交易引用时不能删除。' onConfirm={() => void deleteEsIndex(row.id).then(() => { message.success('删除成功'); esRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm></Permission></Space> },
  ];
  const fieldColumns: ColumnsType<HbaseField | EsField> = ownerType === 'hbase' ? [
    { title: '列族', dataIndex: 'columnFamily', width: 100 }, { title: '字段名', dataIndex: 'fieldName', width: 180 }, { title: '数据类型', dataIndex: 'dataType', width: 150 }, { title: '顺序', dataIndex: 'sortOrder', width: 80 }, { title: '说明', dataIndex: 'description', render: v => <Text value={v} /> },
    { title: '操作', width: 150, render: (_, row) => <Space><Permission code='intranet:dataasset:update'><a onClick={() => setFieldEditing(row)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'><Popconfirm title='确定删除此字段？' onConfirm={() => void removeField(row)}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm></Permission></Space> },
  ] : [
    { title: '字段名', dataIndex: 'fieldName', width: 180 }, { title: '数据类型', dataIndex: 'dataType', width: 110 }, { title: '可搜索', dataIndex: 'searchableFlag', width: 90, render: v => <Tag color={v ? 'success' : 'default'}>{v ? '是' : '否'}</Tag> }, { title: '可排序', dataIndex: 'sortableFlag', width: 90, render: v => <Tag color={v ? 'success' : 'default'}>{v ? '是' : '否'}</Tag> }, { title: '顺序', dataIndex: 'sortOrder', width: 80 }, { title: '说明', dataIndex: 'description', render: v => <Text value={v} /> },
    { title: '操作', width: 150, render: (_, row) => <Space><Permission code='intranet:dataasset:update'><a onClick={() => setFieldEditing(row)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'>{(row as EsField).fieldName !== 'id' && <Popconfirm title='确定删除此字段？' onConfirm={() => void removeField(row)}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm>}</Permission></Space> },
  ];
  const table = <T extends HbaseTable | EsIndex>(ref: React.RefObject<SmartTableRef<Search>>, columns: ColumnsType<T>, request: (p: { pageNum: number; pageSize: number; keyword?: string }) => Promise<{ records: T[]; total: number }>, add: () => void, label: string) => <SmartTable<T, Search>
    ref={ref} rowKey='id' columns={columns} initialSearch={{ keyword: '' }} request={async ({ page, pageSize, search }) => { const data = await request({ pageNum: page, pageSize, keyword: search.keyword }); return { list: data.records, total: data.total }; }} onRequestError={e => message.error(getApiErrorMessage(e))}
    searchRender={({ search, setSearch, submit }) => <Input value={search.keyword} onChange={e => setSearch({ keyword: e.target.value })} onPressEnter={() => submit()} prefix={<SearchOutlined />} placeholder={`搜索${label}`} allowClear />}
    toolbarRender={({ submit }) => <Space><Button icon={<ReloadOutlined />} onClick={() => submit()}>刷新</Button><Permission code='intranet:dataasset:add'><Button type='primary' icon={<PlusOutlined />} onClick={add}>新增{label}</Button></Permission></Space>}
    pagination={{ pageSize: 10, showSizeChanger: false, showTotal: v => `共 ${v} 条记录` }} scroll={{ x: 1250 }} />;
  return <div className='dataasset-page'><div className='page-head'><div><div className='page-kicker'>INTRANET DATA ASSETS</div><h1 className='page-title'>数据资产管理</h1></div></div><div className='table-card'><Tabs activeKey={tab} onChange={setTab} items={[
    { key: 'hbase', label: 'HBase 表', children: table(hbaseRef, hbaseColumns, getHbaseTables, () => setHbaseEditing(null), 'HBase 表') },
    { key: 'es', label: 'ES 索引', children: table(esRef, esColumns, getEsIndexes, () => setEsEditing(null), 'ES 索引') },
  ]} /></div>
    <HbaseTableDialog open={hbaseEditing !== undefined} id={hbaseEditing ?? null} onClose={() => setHbaseEditing(undefined)} onSaved={() => hbaseRef.current?.reload()} />
    <EsIndexDialog open={esEditing !== undefined} id={esEditing ?? null} onClose={() => setEsEditing(undefined)} onSaved={() => esRef.current?.reload()} />
    <Drawer title={fieldOwner ? `${ownerType === 'hbase' ? 'HBase' : 'ES'} 字段 · ${'tableName' in fieldOwner ? fieldOwner.tableName : fieldOwner.indexName}` : '字段'} width={860} open={!!fieldOwner} onClose={() => setFieldOwner(undefined)} extra={<Permission code='intranet:dataasset:add'><Button type='primary' icon={<PlusOutlined />} onClick={() => setFieldEditing(null)}>新增字段</Button></Permission>}><Table rowKey='id' loading={fieldLoading} columns={fieldColumns} dataSource={fields} pagination={false} scroll={{ x: 760 }} /></Drawer>
    {fieldOwner && ownerType === 'hbase' && <HbaseFieldDialog open={fieldEditing !== undefined} tableId={fieldOwner.id} field={(fieldEditing || undefined) as HbaseField | undefined} onClose={() => setFieldEditing(undefined)} onSaved={() => void loadFields()} />}
    {fieldOwner && ownerType === 'es' && <EsFieldDialog open={fieldEditing !== undefined} indexId={fieldOwner.id} field={(fieldEditing || undefined) as EsField | undefined} onClose={() => setFieldEditing(undefined)} onSaved={() => void loadFields()} />}
  </div>;
}
