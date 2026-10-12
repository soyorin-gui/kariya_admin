import { useEffect, useState } from 'react';
import { App, Button, Form, Modal, Popconfirm, Select, Space, Typography } from 'antd';
import { RocketOutlined } from '@ant-design/icons';
import { activateRequirementVersion, configureRequirementImplementation, getRequirementImplementationOptions } from './api';
import type { Requirement, RequirementImplementationOptions } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

type FormValues = { transactionId: number; developerUserId: number };

export function RequirementImplementationDialog({ value, onClose, onCompleted }: { value?: Requirement; onClose: () => void; onCompleted: () => void }) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const [options, setOptions] = useState<RequirementImplementationOptions>({ transactions: [], developers: [] });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!value) return;
    setOptions({ transactions: [], developers: [] });
    form.setFieldsValue({ transactionId: value.transactionId, developerUserId: value.developerUserId } as FormValues);
    void getRequirementImplementationOptions(value.id)
      .then(setOptions)
      .catch(error => message.error(getApiErrorMessage(error, '无法加载实施配置')));
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

  const activate = async () => {
    if (!value) return;
    try {
      setSaving(true);
      await saveAssignments();
      const version = await activateRequirementVersion(value.id);
      message.success(`交易版本 V${version.versionNo} 已生效`);
      onCompleted();
      onClose();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '版本生效失败'));
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
      <Button loading={saving} disabled={Boolean(options.activatedVersionNo)} onClick={() => void save()}>保存配置</Button>
      {options.activatedVersionNo ? <Button type='primary' disabled>已生效 V{options.activatedVersionNo}</Button> : <Popconfirm title='确定生成并生效当前交易版本？' description='生效后快照不可修改，旧版本将转为历史版本。' onConfirm={() => void activate()}>
        <Button type='primary' icon={<RocketOutlined />} loading={saving}>版本生效</Button>
      </Popconfirm>}
    </Space>}
  >
    <Typography.Paragraph type='secondary'>版本将快照保存交易基础信息、请求/响应字段及当前关联的数据资源。</Typography.Paragraph>
    <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 112px' }} labelAlign='left' colon={false}>
      <Form.Item name='transactionId' label='关联交易' rules={[{ required: true, message: '请选择交易' }]}>
        <Select showSearch optionFilterProp='label' disabled={Boolean(value?.transactionId) || Boolean(options.activatedVersionNo)} options={options.transactions} placeholder='请选择交易' />
      </Form.Item>
      <Form.Item name='developerUserId' label='开发负责人' rules={[{ required: true, message: '请选择开发负责人' }]}>
        <Select showSearch optionFilterProp='label' disabled={Boolean(options.activatedVersionNo)} options={options.developers} placeholder='请选择开发负责人' />
      </Form.Item>
    </Form>
  </Modal>;
}
