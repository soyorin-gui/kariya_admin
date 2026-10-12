import { useEffect, useState } from 'react';
import { App, Form, Input, InputNumber, Modal } from 'antd';
import { getTransactionChangeDetail, saveTransactionChangeDetail } from './api';
import type { TransactionChangeDetail } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

const Text = ({ name, label }: { name: keyof TransactionChangeDetail; label: string }) => <Form.Item name={name} label={label}><Input.TextArea rows={3} maxLength={2000} /></Form.Item>;

export function TransactionChangeDetailDialog({ id, onClose }: { id?: number; onClose: () => void }) {
  const [form] = Form.useForm<TransactionChangeDetail>();
  const { message } = App.useApp();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
    form.resetFields();
    void getTransactionChangeDetail(id)
      .then(value => form.setFieldsValue(value ?? {}))
      .catch(error => message.error(getApiErrorMessage(error, '无法加载交易变更明细')));
  }, [form, id, message]);

  const save = async () => {
    if (!id) return;
    try {
      setSaving(true);
      await saveTransactionChangeDetail(id, await form.validateFields());
      message.success('交易变更明细已保存');
      onClose();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  return <Modal className='system-dialog transaction-dialog' width={820} open={Boolean(id)} title='交易变更需求明细' onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
    <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 112px' }} labelAlign='left' colon={false}>
      <Form.Item name='transactionId' label='交易ID'><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Text name='esfChange' label='ESF变更' />
      <Text name='dataLogicChange' label='数据逻辑变更' />
      <Text name='transactionLogicChange' label='交易逻辑变更' />
      <Text name='remarks' label='备注' />
    </Form>
  </Modal>;
}
