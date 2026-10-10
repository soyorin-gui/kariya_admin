import { useEffect, useRef, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin } from 'antd';
import { createTransaction, getTransaction, updateTransaction } from './api';
import type { TransactionRequest } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

const empty: TransactionRequest = {
  transactionName: '',
  transactionCode: '',
  status: 0,
  esfServiceName: '',
  esfServiceOperationId: '',
  esfServiceAddress: '',
  esfServiceOperationName: '',
  printFileMode: 0,
};
export function TransactionDialog({ open, id, onClose, onSaved }: { open: boolean; id: number | null; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<TransactionRequest>();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const editing = id !== null;
  const mode = Form.useWatch('printFileMode', form);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    form.resetFields();
    void (async () => {
      try {
        const value = id === null ? empty : await getTransaction(id);
        if (active) form.setFieldsValue(value);
      } catch (e) {
        message.error(getApiErrorMessage(e, '无法加载交易详情'));
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
      const values = await form.validateFields();
      if (values.printFileMode === 0) {
        values.fileGenerationScope = undefined;
        values.fileNameRule = undefined;
      }
      const result = editing ? await updateTransaction(id!, values) : await createTransaction(values);
      message.success(result.message || '保存成功');
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
      title={editing ? '编辑交易' : '新增交易'}
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
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 112px' }} colon={false} requiredMark={false}>
            <Form.Item name='transactionName' label='交易名称' rules={[{ required: true }]}>
              <Input maxLength={128} placeholder='请输入交易名称' />
            </Form.Item>
            <Form.Item name='transactionCode' label='交易编码' rules={[{ required: true }]}>
              <Input maxLength={64} placeholder='请输入交易唯一编码' />
            </Form.Item>
            <Form.Item name='status' label='交易状态' rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 0, label: '评估中' },
                  { value: 1, label: '开发中' },
                  { value: 2, label: '已投产' },
                  { value: 3, label: '已下线' },
                ]}
              />
            </Form.Item>
            <Form.Item name='label' label='自定义标签'>
              <Input maxLength={10} placeholder='可选，例如：风控' />
            </Form.Item>
            <Form.Item name='esfServiceName' label='ESF 服务名' rules={[{ required: true }]}>
              <Input maxLength={64} placeholder='请输入 ESF 服务名' />
            </Form.Item>
            <Form.Item name='esfServiceOperationId' label='ESF 服务操作 ID' rules={[{ required: true }]}>
              <Input maxLength={64} placeholder='请输入 ESF 服务操作 ID' />
            </Form.Item>
            <Form.Item name='esfServiceAddress' label='ESF 服务地址' rules={[{ required: true }]}>
              <Input maxLength={64} placeholder='请输入 ESF 服务地址，例如 S12345678' />
            </Form.Item>
            <Form.Item name='esfServiceOperationName' label='ESF 服务操作名称' rules={[{ required: true }]}>
              <Input maxLength={64} placeholder='请输入 ESF 服务操作名称' />
            </Form.Item>
            <Form.Item name='businessContact' label='业务对接人'>
              <Input maxLength={32} placeholder='可选，填写姓名' />
            </Form.Item>
            <Form.Item name='dataTimeliness' label='数据时效性'>
              <Input maxLength={50} placeholder='可选，例如实时、T+1' />
            </Form.Item>
            <Form.Item name='queryScope' label='查询范围'>
              <Input maxLength={100} placeholder='可选，例如近 30 个自然日' />
            </Form.Item>
            <Form.Item name='printFileMode' label='打印文件方式'>
              <Radio.Group
                options={[
                  { value: 0, label: '无' },
                  { value: 1, label: '异步' },
                  { value: 2, label: '同步' },
                ]}
              />
            </Form.Item>
            {mode !== 0 && (
              <>
                <Form.Item name='fileGenerationScope' label='文件生成范围'>
                  <Input maxLength={500} placeholder='请输入文件生成范围' />
                </Form.Item>
                <Form.Item name='fileNameRule' label='文件名称规则'>
                  <Input maxLength={255} placeholder='例如 uuid.finish' />
                </Form.Item>
              </>
            )}
          <Form.Item name='description' label='交易说明'>
            <Input.TextArea rows={2} maxLength={1000} placeholder='可选，说明交易用途或边界' />
          </Form.Item>
          <Form.Item name='sortRule' label='排序规则'>
            <Input.TextArea rows={2} maxLength={500} placeholder='可选，例如按交易时间倒序' />
          </Form.Item>
          <Form.Item name='dataValidationScope' label='数据校验范围'>
            <Input.TextArea rows={2} maxLength={500} placeholder='可选，描述数据校验条件' />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
