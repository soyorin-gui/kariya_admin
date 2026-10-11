import { useEffect, useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Radio, Select, Spin } from 'antd';
import { createEsField, createEsIndex, createHbaseField, createHbaseTable, getEsIndex, getHbaseOptions, getHbaseTable, updateEsField, updateEsIndex, updateHbaseField, updateHbaseTable } from './api';
import type { EsField, EsFieldRequest, EsIndexRequest, HbaseField, HbaseFieldRequest, HbaseTableRequest } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

type DialogProps = { open: boolean; id: number | null; onClose: () => void; onSaved: () => void };

function useDialogLoad<T>(open: boolean, id: number | null, load: (id: number) => Promise<T>, empty: Partial<T>, set: (value: Partial<T>) => void, close: () => void) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    void (id === null ? Promise.resolve(empty) : load(id)).then(value => { if (active) set(value); }).catch(e => { message.error(getApiErrorMessage(e, '加载数据失败')); close(); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  // Dialog identity is determined by open/id. The callbacks and empty objects are local render values.
  // Including them would retrigger loading after form.setFieldsValue causes a render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, open]);
  return loading;
}

export function HbaseTableDialog({ open, id, onClose, onSaved }: DialogProps) {
  const [form] = Form.useForm<HbaseTableRequest>(); const { message } = App.useApp(); const [saving, setSaving] = useState(false);
  const loading = useDialogLoad(open, id, getHbaseTable, { marketName: '', tableName: '' }, v => form.setFieldsValue(v), onClose);
  const save = async () => { try { setSaving(true); const data = await form.validateFields(); const result = id === null ? await createHbaseTable(data) : await updateHbaseTable(id, data); message.success(result.message); onSaved(); onClose(); } catch (e) { if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e)); } finally { setSaving(false); } };
  return <Modal className='system-dialog' width={640} title={id === null ? '新增 HBase 表' : '编辑 HBase 表'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
    {loading ? <div className='dialog-loading'><Spin /></div> : <Form form={form} labelCol={{ flex: '0 0 110px' }} colon={false} requiredMark={false}>
      <Form.Item name='marketName' label='集市名' rules={[{ required: true }]}><Input maxLength={64} placeholder='例如 HDS、PBM' /></Form.Item>
      <Form.Item name='tableName' label='HBase 表名' rules={[{ required: true }]}><Input maxLength={128} /></Form.Item>
      <Form.Item name='dataVolume' label='数据量'><Input maxLength={64} placeholder='静态展示文本' /></Form.Item>
      <Form.Item name='dataDepth' label='数据深度'><Input maxLength={64} placeholder='静态展示文本' /></Form.Item>
      <Form.Item name='lockKey' label='锁标识'><Input maxLength={255} placeholder='可为空；仅作资产展示' /></Form.Item>
      <Form.Item name='rowkeyRule' label='RowKey 规则'><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
      <Form.Item name='description' label='说明'><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
    </Form>}
  </Modal>;
}

export function EsIndexDialog({ open, id, onClose, onSaved }: DialogProps) {
  const [form] = Form.useForm<EsIndexRequest>(); const { message } = App.useApp(); const [saving, setSaving] = useState(false); const [options, setOptions] = useState<{ value: number; label: string }[]>([]);
  const loading = useDialogLoad(open, id, getEsIndex, { indexName: '', indexPattern: '', hbaseTableId: undefined as unknown as number }, v => form.setFieldsValue(v), onClose);
  useEffect(() => { if (open) void getHbaseOptions().then(v => setOptions(v.map(x => ({ value: x.id, label: `${x.marketName}:${x.tableName}` })))).catch(e => message.error(getApiErrorMessage(e, '无法加载 HBase 表'))); }, [message, open]);
  const save = async () => { try { setSaving(true); const data = await form.validateFields(); const result = id === null ? await createEsIndex(data) : await updateEsIndex(id, data); message.success(result.message); onSaved(); onClose(); } catch (e) { if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e)); } finally { setSaving(false); } };
  return <Modal className='system-dialog' width={640} title={id === null ? '新增 ES 索引' : '编辑 ES 索引'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
    {loading ? <div className='dialog-loading'><Spin /></div> : <Form form={form} labelCol={{ flex: '0 0 110px' }} colon={false} requiredMark={false}>
      <Form.Item name='indexName' label='索引名称' rules={[{ required: true }]}><Input maxLength={128} /></Form.Item>
      <Form.Item name='indexPattern' label='索引命名模式' rules={[{ required: true }]}><Input maxLength={255} placeholder='例如 order_yyyyMM' /></Form.Item>
      <Form.Item name='partitionRule' label='分片规则'><Input maxLength={64} placeholder='自由文本，例如按月' /></Form.Item>
      <Form.Item name='hbaseTableId' label='HBase 表' rules={[{ required: true }]}><Select showSearch optionFilterProp='label' options={options} /></Form.Item>
      <Form.Item name='description' label='说明'><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
    </Form>}
  </Modal>;
}

export function HbaseFieldDialog({ open, tableId, field, onClose, onSaved }: { open: boolean; tableId: number; field?: HbaseField; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<HbaseFieldRequest>(); const { message } = App.useApp(); const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) form.setFieldsValue(field || { columnFamily: 'info', fieldName: '', sortOrder: 0 }); }, [field, form, open]);
  const save = async () => { try { setSaving(true); const data = await form.validateFields(); const result = field ? await updateHbaseField(tableId, field.id, data) : await createHbaseField(tableId, data); message.success(result.message); onSaved(); onClose(); } catch (e) { if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e)); } finally { setSaving(false); } };
  return <Modal className='system-dialog' title={field ? '编辑 HBase 字段' : '新增 HBase 字段'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden><Form form={form} labelCol={{ flex: '0 0 90px' }} colon={false} requiredMark={false}>
    <Form.Item name='columnFamily' label='列族' rules={[{ required: true }]}><Input maxLength={64} /></Form.Item>
    <Form.Item name='fieldName' label='字段名' rules={[{ required: true }]}><Input maxLength={128} /></Form.Item>
    <Form.Item name='dataType' label='数据类型'><Input maxLength={128} placeholder='例如 varchar(32)' /></Form.Item>
    <Form.Item name='sortOrder' label='展示顺序' rules={[{ required: true }]}><InputNumber precision={0} style={{ width: '100%' }} /></Form.Item>
    <Form.Item name='description' label='说明'><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
  </Form></Modal>;
}

export function EsFieldDialog({ open, indexId, field, onClose, onSaved }: { open: boolean; indexId: number; field?: EsField; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<EsFieldRequest>(); const { message } = App.useApp(); const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) form.setFieldsValue(field || { fieldName: '', dataType: 'keyword', searchableFlag: 0, sortableFlag: 0, sortOrder: 0 }); }, [field, form, open]);
  const save = async () => { try { setSaving(true); const data = await form.validateFields(); const result = field ? await updateEsField(indexId, field.id, data) : await createEsField(indexId, data); message.success(result.message); onSaved(); onClose(); } catch (e) { if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e)); } finally { setSaving(false); } };
  return <Modal className='system-dialog' title={field ? '编辑 ES 字段' : '新增 ES 字段'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden><Form form={form} labelCol={{ flex: '0 0 90px' }} colon={false} requiredMark={false}>
    <Form.Item name='fieldName' label='字段名' rules={[{ required: true }]}><Input maxLength={128} disabled={field?.fieldName === 'id'} /></Form.Item>
    <Form.Item name='dataType' label='数据类型' rules={[{ required: true }]}><Select options={['keyword', 'numeric', 'date', 'text'].map(value => ({ value, label: value }))} /></Form.Item>
    <Form.Item name='searchableFlag' label='可搜索'><Radio.Group options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} /></Form.Item>
    <Form.Item name='sortableFlag' label='可排序'><Radio.Group options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} /></Form.Item>
    <Form.Item name='sortOrder' label='展示顺序' rules={[{ required: true }]}><InputNumber precision={0} style={{ width: '100%' }} /></Form.Item>
    <Form.Item name='description' label='说明'><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
  </Form></Modal>;
}
