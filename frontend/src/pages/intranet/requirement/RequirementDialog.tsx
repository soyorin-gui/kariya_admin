import { useEffect, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { App, DatePicker, Form, Input, Modal, Select, Spin } from 'antd';
import { createRequirement, getRequirement, updateRequirement } from './api';
import type { RequirementRequest } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

const types = [
  { value: 1, label: '新增交易' },
  { value: 2, label: '交易变更' },
  { value: 3, label: '交易调用' },
];
export function RequirementDialog({ open, id, onClose, onSaved }: { open: boolean; id: number | null; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<RequirementRequest>();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    form.resetFields();
    void (async () => {
      try {
        const value = id === null ? undefined : await getRequirement(id);
        if (active && value) form.setFieldsValue({ ...value, expectedOnlineDate: value.expectedOnlineDate ? dayjs(value.expectedOnlineDate) : undefined } as never);
      } catch (e) {
        message.error(getApiErrorMessage(e, '无法加载需求详情'));
        onClose();
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [form, id, message, onClose, open]);
  const save = async () => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const value = await form.validateFields();
      const payload = { ...value, expectedOnlineDate: (value.expectedOnlineDate as unknown as { format?: (pattern: string) => string })?.format?.('YYYY-MM-DD') ?? value.expectedOnlineDate };
      id === null ? await createRequirement(payload) : await updateRequirement(id, payload);
      message.success('需求已保存');
      onSaved();
      onClose();
    } catch (e) {
      if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e, '保存失败'));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };
  return (
    <Modal
      className='system-dialog transaction-dialog'
      width={660}
      title={id === null ? '新建需求概要' : '编辑需求概要'}
      open={open}
      onCancel={onClose}
      onOk={() => void save()}
      confirmLoading={saving}
      destroyOnHidden
    >
      {loading ? (
        <div className='dialog-loading'>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} requiredMark={false}>
          <Form.Item name='oaNo' label='OA编号' rules={[{ required: true }]}>
            <Input maxLength={50} placeholder='请输入 OA 编号' />
          </Form.Item>
          <Form.Item name='title' label='需求名称' rules={[{ required: true }]}>
            <Input maxLength={255} placeholder='请输入需求名称' />
          </Form.Item>
          <Form.Item name='requirementType' label='需求类型' rules={[{ required: true }]}>
            <Select disabled={id !== null} placeholder='请选择需求类型' options={types} />
          </Form.Item>
          <Form.Item name='expectedOnlineDate' label='期望上线时间'>
            <DatePicker style={{ width: '100%' }} placeholder='请选择日期' />
          </Form.Item>
          <Form.Item name='description' label='需求描述'>
            <Input.TextArea rows={3} maxLength={1000} placeholder='请输入需求背景、范围或补充说明' />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
