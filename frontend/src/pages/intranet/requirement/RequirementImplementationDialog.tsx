import { useEffect, useState } from 'react';
import { App, Button, Form, Modal, Select, Skeleton, Space, Typography } from 'antd';
import { configureRequirementImplementation, getRequirementImplementationOptions } from './api';
import type { Requirement, RequirementImplementationOptions } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

type FormValues = { transactionId: number; developerUserId?: number };

export function RequirementImplementationDialog({ value, onClose, onCompleted }: { value?: Requirement; onClose: () => void; onCompleted: () => void }) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const [options, setOptions] = useState<RequirementImplementationOptions>({ transactions: [], developers: [] });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!value) return;
    setLoading(true);
    setOptions({ transactions: [], developers: [] });
    form.setFieldsValue({ transactionId: value.transactionId, developerUserId: value.developerUserId } as FormValues);
    void getRequirementImplementationOptions(value.id)
      .then(setOptions)
      .catch(error => message.error(getApiErrorMessage(error, '无法加载实施配置')))
      .finally(() => setLoading(false));
  }, [form, message, value]);

  const saveAssignments = async () => {
    if (!value) return;
    const fields = await form.validateFields();
    await configureRequirementImplementation(value.id, fields.transactionId, fields.developerUserId);
  };

  const save = async () => {
    try {
      setSaving(true);
      await saveAssignments();
      message.success('实施配置已保存');
      onCompleted();
      onClose();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存实施配置失败'));
    } finally {
      setSaving(false);
    }
  };

  return <Modal
    className='system-dialog transaction-dialog'
    width={620}
    open={Boolean(value)}
    title={value ? `实施配置 · ${value.title}` : '实施配置'}
    onCancel={onClose}
    destroyOnHidden
    footer={<Space>
      <Button onClick={onClose}>取消</Button>
      <Button type='primary' loading={saving || loading} disabled={Boolean(options.activatedVersionNo)} onClick={() => void save()}>保存配置</Button>
    </Space>}
  >
    <Typography.Paragraph type='secondary'>{value?.requirementType === 3 ? '请选择本次调用关联的未下线交易。' : '保存后仅完成交易和开发负责人的实施配置；版本快照与状态变更将在需求列表点击“投产”后执行。'}</Typography.Paragraph>
    {loading ? <Skeleton active paragraph={{ rows: 3 }} /> : <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 112px' }} labelAlign='left' colon={false}>
      <Form.Item name='transactionId' label='关联交易' rules={[{ required: true, message: '请选择交易' }]}>
        <Select showSearch optionFilterProp='label' disabled={Boolean(value?.transactionId) || Boolean(options.activatedVersionNo)} options={options.transactions} placeholder='请选择交易' />
      </Form.Item>
      {value?.requirementType !== 3 && <Form.Item name='developerUserId' label='开发负责人' rules={[{ required: true, message: '请选择开发负责人' }]}>
        <Select showSearch optionFilterProp='label' disabled={Boolean(options.activatedVersionNo)} options={options.developers} placeholder='请选择开发负责人' />
      </Form.Item>}
    </Form>}
  </Modal>;
}
