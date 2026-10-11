import { useEffect, useState } from 'react';
import { Alert, App, Form, Modal, Select, Spin } from 'antd';
import { getEsOptions, getHbaseOptions } from '../dataasset/api';
import type { EsIndex, HbaseTable } from '../dataasset/types';
import { getTransactionDataResources, updateTransactionDataResources } from './api';
import type { TransactionDataResources } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

export function DataResourceDialog({ transactionId, onClose }: { transactionId?: number; onClose: () => void }) {
  const [form] = Form.useForm<TransactionDataResources>(); const { message } = App.useApp();
  const [loading, setLoading] = useState(false); const [saving, setSaving] = useState(false); const [hbase, setHbase] = useState<HbaseTable[]>([]); const [es, setEs] = useState<EsIndex[]>([]);
  useEffect(() => {
    if (!transactionId) return; let active = true; setLoading(true); form.resetFields();
    void Promise.all([getTransactionDataResources(transactionId), getHbaseOptions(), getEsOptions()]).then(([resources, hbaseOptions, esOptions]) => { if (!active) return; form.setFieldsValue(resources); setHbase(hbaseOptions); setEs(esOptions); }).catch(e => { message.error(getApiErrorMessage(e, '无法加载数据资源')); onClose(); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [form, message, onClose, transactionId]);
  const save = async () => { if (!transactionId) return; try { setSaving(true); const result = await updateTransactionDataResources(transactionId, await form.validateFields()); message.success(result.message); onClose(); } catch (e) { if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e)); } finally { setSaving(false); } };
  return <Modal className='system-dialog' width={680} title='配置交易数据资源' open={!!transactionId} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
    {loading ? <div className='dialog-loading'><Spin /></div> : <Form form={form} layout='vertical' initialValues={{ hbaseTableIds: [], esIndexIds: [] }}>
      <Alert type='info' showIcon message='这里记录交易涉及的资源集合，不表达查询分支、先后顺序或多表整合逻辑。' />
      <Form.Item name='hbaseTableIds' label='直接查询的 HBase 表'><Select mode='multiple' allowClear showSearch optionFilterProp='label' placeholder='可选；入参可直接定位 RowKey 时配置' options={hbase.map(x => ({ value: x.id, label: `${x.marketName}:${x.tableName}` }))} /></Form.Item>
      <Form.Item name='esIndexIds' label='先查 ES 的索引'><Select mode='multiple' allowClear showSearch optionFilterProp='label' placeholder='可选；所选索引必须包含固定 id 字段' options={es.map(x => ({ value: x.id, label: `${x.indexName}（${x.indexPattern}） → ${x.hbaseMarketName}:${x.hbaseTableName}` }))} /></Form.Item>
    </Form>}
  </Modal>;
}
