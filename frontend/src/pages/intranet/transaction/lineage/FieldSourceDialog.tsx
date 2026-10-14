import { useEffect, useState } from 'react';
import { App, Button, Form, Input, Modal, Select, Space, Spin, Table } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { getLineageOptions, getTransactionFieldSource, saveTransactionFieldSource } from '../api';
import type { FieldSourceRelation, FieldSourceType, LineageOption, TransactionMessageField } from '../types';
import { getApiErrorMessage } from '../../../../utils/apiError';

const modes = [
  { value: 'PHYSICAL', label: '物理字段来源' },
  { value: 'CALLER_INPUT', label: '调用方入参' },
  { value: 'CONSTANT', label: '固定常量' },
  { value: 'SYSTEM_GENERATED', label: '系统生成' },
  { value: 'COMPUTED', label: '计算得到' },
];
type EditableRelation = FieldSourceRelation & { key: string };
let relationSequence = 0;
const createRelationKey = () => `relation-${++relationSequence}`;

export function FieldSourceDialog({ field, open, onClose }: { field?: TransactionMessageField; open: boolean; onClose: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ sourceType: FieldSourceType; sourceDetail?: string }>();
  const [options, setOptions] = useState<LineageOption[]>([]);
  const [relations, setRelations] = useState<EditableRelation[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const sourceType = Form.useWatch('sourceType', form);

  useEffect(() => {
    if (!open || !field) return;
    setLoading(true);
    void Promise.all([getLineageOptions(), getTransactionFieldSource(field.id)])
      .then(([sourceOptions, source]) => {
        setOptions(sourceOptions);
        setRelations(source.relations.map((relation) => ({ ...relation, key: createRelationKey() })));
        form.setFieldsValue({ sourceType: source.sourceType === 'UNCONFIGURED' ? 'CALLER_INPUT' : source.sourceType, sourceDetail: source.sourceDetail });
      })
      .catch((error) => message.error(getApiErrorMessage(error, '无法加载字段来源')))
      .finally(() => setLoading(false));
  }, [field, form, message, open]);

  const updateRelation = (index: number, change: Partial<FieldSourceRelation>) => setRelations((all) => all.map((item, current) => (current === index ? { ...item, ...change } : item)));
  const save = async () => {
    if (!field) return;
    try {
      setSaving(true);
      const values = await form.validateFields();
      await saveTransactionFieldSource(field.id, { ...values, relations: relations.map(({ key: _key, ...relation }) => relation) });
      message.success('字段来源已保存');
      onClose();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const physical = sourceType === 'PHYSICAL' || sourceType === 'COMPUTED';
  return (
    <Modal width={1080} title={field ? `字段来源 · ${field.fieldNameCn}` : '字段来源'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
      <Spin spinning={loading} tip='正在加载字段来源…'>
        <Form form={form} layout='horizontal' labelCol={{ span: 5 }}>
          <Form.Item name='sourceType' label='取值方式' rules={[{ required: true }]}>
            <Select options={modes} />
          </Form.Item>
          <Form.Item name='sourceDetail' label={sourceType === 'CONSTANT' ? '常量值' : sourceType === 'COMPUTED' ? '计算说明' : '取值说明'}>
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
        {physical && (
          <>
            <Space style={{ marginBottom: 12 }}>
              <Button icon={<PlusOutlined />} disabled={!options.length} onClick={() => setRelations((all) => [...all, { key: createRelationKey(), sourceType: 'HBASE_FIELD', sourceFieldId: options[0].sourceFieldId, relationType: 'DIRECT' }])}>
                关联物理字段
              </Button>
              {!options.length && '请先维护 HBase 或 ES 字段。'}
            </Space>
            <Table
              size='small'
              rowKey='key'
              pagination={false}
              dataSource={relations}
              scroll={{ x: 1000, y: 300 }}
              columns={[
                { title: '来源字段', width: 280, render: (_, relation: EditableRelation, index) => <Select value={`${relation.sourceType}:${relation.sourceFieldId}`} style={{ width: '100%' }} options={options.map((option) => ({ value: `${option.sourceType}:${option.sourceFieldId}`, label: option.label }))} onChange={(value) => { const [type, fieldId] = value.split(':'); updateRelation(index, { sourceType: type as FieldSourceRelation['sourceType'], sourceFieldId: Number(fieldId) }); }} /> },
                { title: '关系', width: 150, render: (_, relation: EditableRelation, index) => <Select value={relation.relationType} options={[{ value: 'DIRECT', label: '直接读取' }, { value: 'DERIVED', label: '加工得到' }, { value: 'QUERY_CONDITION', label: '查询条件' }]} onChange={(value) => updateRelation(index, { relationType: value })} /> },
                { title: '表达式/条件', width: 250, render: (_, relation: EditableRelation, index) => <Input value={relation.expression} maxLength={2000} placeholder='例如 amount * 100' onChange={(event) => updateRelation(index, { expression: event.target.value })} /> },
                { title: '说明', width: 200, render: (_, relation: EditableRelation, index) => <Input value={relation.remark} maxLength={1000} onChange={(event) => updateRelation(index, { remark: event.target.value })} /> },
                { title: '操作', width: 70, render: (_, __, index) => <Button type='link' danger icon={<DeleteOutlined />} onClick={() => setRelations((all) => all.filter((_, current) => current !== index))} /> },
              ]}
            />
          </>
        )}
      </Spin>
    </Modal>
  );
}
