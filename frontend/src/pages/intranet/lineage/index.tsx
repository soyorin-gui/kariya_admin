import { useEffect, useState } from 'react';
import { App, Card, Empty, Select, Space, Spin, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { LineageGraphCanvas } from './LineageGraphCanvas';
import { getLineageGraph, getLineageSearchOptions } from '../transaction/api';
import type { LineageGraph, LineageSearchOption } from '../transaction/types';
import { getApiErrorMessage } from '../../../utils/apiError';
import './index.css';

type EntryType = LineageSearchOption['entryType'];
const entryTypes: Array<{ value: EntryType; label: string }> = [{ value: 'TRANSACTION', label: '交易' }, { value: 'TRANSACTION_FIELD', label: '交易字段' }, { value: 'HBASE_TABLE', label: 'HBase 表' }, { value: 'HBASE_FIELD', label: 'HBase 字段' }];
const colors: Record<string, string> = { HBASE_FIELD: '#1677ff', ES_FIELD: '#722ed1', HBASE_TABLE: '#13a8a8', ES_INDEX: '#531dab', TRANSACTION_FIELD: '#d46b08', TRANSACTION: '#389e0d' };
const nodeNames: Record<string, string> = { HBASE_FIELD: 'HBase 字段', ES_FIELD: 'ES 字段', HBASE_TABLE: 'HBase 表', ES_INDEX: 'ES 索引', TRANSACTION_FIELD: '交易字段', TRANSACTION: '交易' };

export default function LineageAnalysisPage() {
  const { message } = App.useApp();
  const [params] = useSearchParams();
  const initialType = (params.get('entryType') as EntryType) || 'TRANSACTION';
  const initialId = Number(params.get('entryId')) || undefined;
  const [entryType, setEntryType] = useState<EntryType>(initialType);
  const [options, setOptions] = useState<LineageSearchOption[]>([]);
  const [selected, setSelected] = useState<LineageSearchOption | undefined>();
  const [loading, setLoading] = useState(false);
  const [graph, setGraph] = useState<LineageGraph>();
  const loadGraph = (type: EntryType, id: number) => {
    setLoading(true);
    void getLineageGraph(type, id).then(setGraph).catch((error) => message.error(getApiErrorMessage(error, '无法加载血缘图谱'))).finally(() => setLoading(false));
  };
  useEffect(() => { if (initialId) loadGraph(initialType, initialId); }, []);
  const search = (keyword = '') => {
    void getLineageSearchOptions(entryType, keyword).then(setOptions).catch((error) => message.error(getApiErrorMessage(error, '搜索失败')));
  };
  return <div className='page-fill lineage-page'><div className='page-head'><div><div className='page-kicker'>INTRANET LINEAGE</div><h1 className='page-title'>血缘分析</h1><p className='lineage-description'>基于已维护的 HBase/ES 与交易字段关系，展示确定性影响路径。</p></div></div><Card className='lineage-search-card'><Space wrap size='middle'><Select value={entryType} options={entryTypes} style={{ width: 160 }} onChange={(value) => { setEntryType(value); setSelected(undefined); setOptions([]); setGraph(undefined); }} /><Select showSearch filterOption={false} value={selected?.entryId} style={{ width: 440 }} placeholder='选择或搜索分析对象' options={options.map((item) => ({ value: item.entryId, label: `${item.label} · ${item.detail}` }))} onOpenChange={(open) => { if (open && !options.length) search(); }} onSearch={search} onChange={(id) => { const item = options.find((option) => option.entryId === id); if (item) { setSelected(item); loadGraph(item.entryType, item.entryId); } }} /></Space></Card><Card className='lineage-graph-card' title={selected ? `血缘图谱 · ${selected.label}` : initialId ? '血缘图谱' : '等待搜索'} extra={graph?.nodes.length ? <Space size={4} wrap>{Object.entries(nodeNames).map(([type, label]) => <Tag key={type} color={colors[type]}>{label}</Tag>)}</Space> : undefined}><Spin spinning={loading} tip='正在生成确定性血缘图谱…'>{graph?.nodes.length ? <LineageGraphCanvas data={graph} /> : !loading && <Empty description='请选择分析对象并搜索；系统仅展示已维护的血缘关系。' />}</Spin></Card></div>;
}
