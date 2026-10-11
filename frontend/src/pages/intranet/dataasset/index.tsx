import { useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App, Button, Input, Popconfirm, Space, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { Permission } from '../../../permission/Permission';
import { getApiErrorMessage } from '../../../utils/apiError';
import { EllipsisText } from '../../../components/EllipsisText';
import { deleteEsIndex, deleteHbaseTable, getEsIndexes, getHbaseTables } from './api';
import type { EsIndex, HbaseTable } from './types';
import { EsIndexDialog, HbaseTableDialog } from './AssetDialogs';
import './index.css';

type Search = { keyword: string };

export default function DataAssetPage() {
  const { message } = App.useApp();
  const navigate = useNavigate(); const [params, setParams] = useSearchParams();
  const hbaseRef = useRef<SmartTableRef<Search>>(null); const esRef = useRef<SmartTableRef<Search>>(null);
  const tab = params.get('tab') === 'es' ? 'es' : 'hbase';
  const [hbaseEditing, setHbaseEditing] = useState<number | null | undefined>(); const [esEditing, setEsEditing] = useState<number | null | undefined>();
  const openFields = (assetType: 'HBASE' | 'ES', assetId: number) => navigate({ pathname: '/intranet/dataasset/fields', search: `?assetType=${assetType}&assetId=${assetId}` });

  const hbaseColumns: ColumnsType<HbaseTable> = [
    { title: '集市名', dataIndex: 'marketName', width: 110 }, { title: 'HBase 表名', dataIndex: 'tableName', width: 200 },
    { title: '数据量', dataIndex: 'dataVolume', width: 120, render: v => <EllipsisText value={v} /> }, { title: '数据深度', dataIndex: 'dataDepth', width: 120, render: v => <EllipsisText value={v} /> },
    { title: '锁标识', dataIndex: 'lockKey', width: 150, render: v => <EllipsisText value={v} /> }, { title: 'RowKey 规则', dataIndex: 'rowkeyRule', width: 220, render: v => <EllipsisText value={v} /> },
    { title: '更新时间', dataIndex: 'updatedTime', width: 170, render: v => v?.replace('T', ' ') },
    { title: '操作', fixed: 'right', width: 230, render: (_, row) => <Space><a onClick={() => openFields('HBASE', row.id)}>字段设计</a><Permission code='intranet:dataasset:update'><a onClick={() => setHbaseEditing(row.id)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'><Popconfirm title='确定删除此 HBase 表？' description='存在 ES 或交易引用时不能删除。' onConfirm={() => void deleteHbaseTable(row.id).then(() => { message.success('删除成功'); hbaseRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm></Permission></Space> },
  ];
  const esColumns: ColumnsType<EsIndex> = [
    { title: '索引名称', dataIndex: 'indexName', width: 180 }, { title: '索引命名模式', dataIndex: 'indexPattern', width: 220, render: v => <EllipsisText value={v} /> },
    { title: '分片规则', dataIndex: 'partitionRule', width: 130, render: v => <EllipsisText value={v} /> }, { title: '对应 HBase 表', width: 200, render: (_, row) => `${row.hbaseMarketName}:${row.hbaseTableName}` },
    { title: '说明', dataIndex: 'description', width: 220, render: v => <EllipsisText value={v} /> }, { title: '更新时间', dataIndex: 'updatedTime', width: 170, render: v => v?.replace('T', ' ') },
    { title: '操作', fixed: 'right', width: 230, render: (_, row) => <Space><a onClick={() => openFields('ES', row.id)}>字段设计</a><Permission code='intranet:dataasset:update'><a onClick={() => setEsEditing(row.id)}><EditOutlined /> 编辑</a></Permission><Permission code='intranet:dataasset:delete'><Popconfirm title='确定删除此 ES 索引？' description='存在交易引用时不能删除。' onConfirm={() => void deleteEsIndex(row.id).then(() => { message.success('删除成功'); esRef.current?.reload(); }).catch(e => message.error(getApiErrorMessage(e)))}><a className='danger'><DeleteOutlined /> 删除</a></Popconfirm></Permission></Space> },
  ];
  const table = <T extends HbaseTable | EsIndex>(ref: React.RefObject<SmartTableRef<Search>>, columns: ColumnsType<T>, request: (p: { pageNum: number; pageSize: number; keyword?: string }) => Promise<{ records: T[]; total: number }>, add: () => void, label: string) => <SmartTable<T, Search>
    ref={ref} rowKey='id' columns={columns} initialSearch={{ keyword: '' }} request={async ({ page, pageSize, search }) => { const data = await request({ pageNum: page, pageSize, keyword: search.keyword }); return { list: data.records, total: data.total }; }} onRequestError={e => message.error(getApiErrorMessage(e))} toolbarClassName='table-toolbar'
    searchRender={({ search, setSearch, submit }) => <Input value={search.keyword} onChange={e => setSearch({ keyword: e.target.value })} onPressEnter={() => submit()} prefix={<SearchOutlined />} placeholder={`搜索${label}`} allowClear />}
    toolbarRender={({ submit }) => <Space><Button icon={<ReloadOutlined />} onClick={() => submit()}>刷新</Button><Permission code='intranet:dataasset:add'><Button type='primary' icon={<PlusOutlined />} onClick={add}>新增{label}</Button></Permission></Space>}
    pagination={{ pageSize: 10, showSizeChanger: false, showTotal: v => `共 ${v} 条记录` }} scroll={{ x: 1250 }} />;
  return <div className='dataasset-page'><div className='page-head'><div><div className='page-kicker'>INTRANET DATA ASSETS</div><h1 className='page-title'>数据资产管理</h1></div></div><div className='table-card'><Tabs activeKey={tab} onChange={(key) => setParams(key === 'es' ? { tab: 'es' } : {})} items={[
    { key: 'hbase', label: 'HBase 表', children: table(hbaseRef, hbaseColumns, getHbaseTables, () => setHbaseEditing(null), 'HBase 表') },
    { key: 'es', label: 'ES 索引', children: table(esRef, esColumns, getEsIndexes, () => setEsEditing(null), 'ES 索引') },
  ]} /></div>
    <HbaseTableDialog open={hbaseEditing !== undefined} id={hbaseEditing ?? null} onClose={() => setHbaseEditing(undefined)} onSaved={() => hbaseRef.current?.reload()} />
    <EsIndexDialog open={esEditing !== undefined} id={esEditing ?? null} onClose={() => setEsEditing(undefined)} onSaved={() => esRef.current?.reload()} />
  </div>;
}
