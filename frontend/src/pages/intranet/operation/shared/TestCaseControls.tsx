import { useEffect, useState } from 'react';
import { App, Button, Form, Input, Modal, Popconfirm, Select, Space } from 'antd';
import { CopyOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { usePermission } from '../../../../permission/Permission';
import type { DefinitionTarget } from './definitions';
import { copyOperationTestCase, createOperationTestCase, deleteOperationTestCase, getOperationTestCases, updateOperationTestCase } from './testCases';
import type { OperationTestCase } from './testCases';

interface CaseFormValue { caseName: string; description?: string }

export function TestCaseControls({ transactionId, target, targets, getPayload, onApply, onSelectionChange }: {
  transactionId?: number;
  target?: DefinitionTarget;
  targets: DefinitionTarget[];
  getPayload: () => Record<string, unknown>;
  onApply: (payload: Record<string, unknown>) => void;
  onSelectionChange?: (id?: number) => void;
}) {
  const { message } = App.useApp();
  const hasPermission = usePermission();
  const canManage = hasPermission('intranet:operation:test-case:manage');
  const [form] = Form.useForm<CaseFormValue>();
  const [cases, setCases] = useState<OperationTestCase[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [editing, setEditing] = useState<OperationTestCase>();
  const [modalOpen, setModalOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);
  const [copyTargetKey, setCopyTargetKey] = useState<string>();
  const [saving, setSaving] = useState(false);
  const selected = cases.find((item) => item.id === selectedId);

  const reload = async (nextSelectedId?: number) => {
    if (!transactionId || !target) { setCases([]); setSelectedId(undefined); onSelectionChange?.(undefined); return; }
    try {
      const values = await getOperationTestCases({ transactionId, bindingType: target.bindingType, transactionVersionId: target.transactionVersionId });
      setCases(values);
      const resolved = nextSelectedId && values.some((item) => item.id === nextSelectedId) ? nextSelectedId : undefined;
      setSelectedId(resolved);
      onSelectionChange?.(resolved);
    } catch (error) { message.error(getApiErrorMessage(error, '无法加载测试案例')); }
  };

  useEffect(() => { void reload(); }, [transactionId, target?.key]);

  const selectCase = (id?: number) => {
    setSelectedId(id);
    onSelectionChange?.(id);
    const value = cases.find((item) => item.id === id);
    if (!value) return;
    try { onApply(JSON.parse(value.requestPayload) as Record<string, unknown>); }
    catch { message.error('案例请求参数不是合法 JSON'); }
  };

  const openCreate = () => { setEditing(undefined); form.resetFields(); setModalOpen(true); };
  const openEdit = () => { if (!selected) return; setEditing(selected); form.setFieldsValue({ caseName: selected.caseName, description: selected.description }); setModalOpen(true); };
  const save = async () => {
    if (!transactionId || !target) return;
    const metadata = await form.validateFields();
    setSaving(true);
    try {
      const data = { transactionId, bindingType: target.bindingType, transactionVersionId: target.transactionVersionId, ...metadata, requestPayload: JSON.stringify(getPayload()) };
      const saved = editing ? await updateOperationTestCase(editing.id, data) : await createOperationTestCase(data);
      setModalOpen(false);
      await reload(saved.id);
      message.success(editing ? '测试案例已更新' : '测试案例已新增');
    } catch (error) { message.error(getApiErrorMessage(error, '无法保存测试案例')); }
    finally { setSaving(false); }
  };

  const remove = async () => {
    if (!selected) return;
    try { await deleteOperationTestCase(selected.id); await reload(); message.success('测试案例已删除'); }
    catch (error) { message.error(getApiErrorMessage(error, '无法删除测试案例')); }
  };

  const copy = async () => {
    if (!selected || !copyTargetKey) return;
    const copyTarget = targets.find((item) => item.key === copyTargetKey);
    if (!copyTarget) return;
    setSaving(true);
    try {
      await copyOperationTestCase(selected.id, { bindingType: copyTarget.bindingType, transactionVersionId: copyTarget.transactionVersionId, caseName: `${selected.caseName} - 副本` });
      setCopyOpen(false);
      message.success(`案例已复制到${copyTarget.label}`);
    } catch (error) { message.error(getApiErrorMessage(error, '无法复制测试案例')); }
    finally { setSaving(false); }
  };

  return <>
    <Space wrap>
      <Select allowClear value={selectedId} placeholder='请选择测试案例' style={{ width: 240 }} options={cases.map((item) => ({ value: item.id, label: item.caseName }))} onChange={selectCase} disabled={!target} />
      {canManage && <>
        <Button icon={<PlusOutlined />} disabled={!target} onClick={openCreate}>新增案例</Button>
        <Button icon={<EditOutlined />} disabled={!selected} onClick={openEdit}>更新案例</Button>
        <Button icon={<CopyOutlined />} disabled={!selected} onClick={() => { setCopyTargetKey(undefined); setCopyOpen(true); }}>复制到</Button>
        <Popconfirm title='确定物理删除该测试案例吗？' onConfirm={() => void remove()}><Button danger icon={<DeleteOutlined />} disabled={!selected}>删除</Button></Popconfirm>
      </>}
    </Space>
    <Modal title={editing ? '更新测试案例' : '新增测试案例'} open={modalOpen} confirmLoading={saving} onOk={() => void save()} onCancel={() => setModalOpen(false)} destroyOnHidden>
      <Form form={form} layout='vertical' preserve={false}>
        <Form.Item name='caseName' label='案例名称' rules={[{ required: true, message: '请输入案例名称' }, { max: 128 }]}><Input /></Form.Item>
        <Form.Item name='description' label='案例说明' rules={[{ max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
      </Form>
    </Modal>
    <Modal title='复制测试案例' open={copyOpen} confirmLoading={saving} okButtonProps={{ disabled: !copyTargetKey }} onOk={() => void copy()} onCancel={() => setCopyOpen(false)} destroyOnHidden>
      <Select value={copyTargetKey} placeholder='请选择目标字段定义' style={{ width: '100%' }} options={targets.filter((item) => item.key !== target?.key).map((item) => ({ value: item.key, label: item.label }))} onChange={setCopyTargetKey} />
    </Modal>
  </>;
}
