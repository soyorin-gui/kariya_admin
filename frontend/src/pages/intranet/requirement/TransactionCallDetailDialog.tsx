import { useEffect, useState } from 'react';
import { App, DatePicker, Form, Input, Modal, Select } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { getTransactionCallDetail, saveTransactionCallDetail } from './api';
import type { TransactionCallDetail } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

type FormValues = Omit<TransactionCallDetail, 'expectedOnlineDate'> & { expectedOnlineDate?: Dayjs };
const Text = ({ name, label }: { name: keyof FormValues; label: string }) => <Form.Item name={name} label={label}><Input.TextArea rows={2} maxLength={2000} /></Form.Item>;

export function TransactionCallDetailDialog({ id, onClose }: { id?: number; onClose: () => void }) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
    form.resetFields();
    void getTransactionCallDetail(id)
      .then(value => form.setFieldsValue({ ...value, expectedOnlineDate: value?.expectedOnlineDate ? dayjs(value.expectedOnlineDate) : undefined }))
      .catch(error => message.error(getApiErrorMessage(error, '无法加载交易调用明细')));
  }, [form, id, message]);

  const save = async () => {
    if (!id) return;
    try {
      setSaving(true);
      const value = await form.validateFields();
      await saveTransactionCallDetail(id, { ...value, expectedOnlineDate: value.expectedOnlineDate?.format('YYYY-MM-DD') });
      message.success('交易调用明细已保存');
      onClose();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  return <Modal className='system-dialog transaction-dialog' width={820} open={Boolean(id)} title='交易调用需求明细' onCancel={onClose} onOk={() => void save()} confirmLoading={saving} destroyOnHidden>
    <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 128px' }} labelAlign='left' colon={false}>
      <Form.Item name='costBusinessContact' label='成本业务联系人'><Input /></Form.Item>
      <Form.Item name='businessDepartment' label='业务部门'><Input /></Form.Item>
      <Form.Item name='oaContactNo' label='OA联系单号'><Input /></Form.Item>
      <Text name='interfaceDataFields' label='接口数据字段' />
      <Form.Item name='callingSystem' label='调用方系统'><Input /></Form.Item>
      <Form.Item name='callingSystemOwner' label='调用方负责人'><Input /></Form.Item>
      <Form.Item name='expectedOnlineDate' label='期望上线时间'><DatePicker style={{ width: '100%' }} /></Form.Item>
      <Form.Item name='userQueryBehavior' label='用户查询行为'><Select allowClear options={[{ value: 'INTERNAL', label: '对客' }, { value: 'EXTERNAL', label: '非对客' }]} /></Form.Item>
      <Form.Item name='transactionCode' label='交易码'><Input /></Form.Item>
      <Form.Item name='currentCapabilityMetFlag' label='现有能力是否满足'><Select allowClear options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} /></Form.Item>
      <Text name='thirdCategoryDetail' label='三超详细描述' />
      <Text name='fiveCategoryDetail' label='五敏详细描述' />
      <Text name='architectureDetail' label='企架详细描述' />
      <Text name='remarks' label='备注' />
    </Form>
  </Modal>;
}
