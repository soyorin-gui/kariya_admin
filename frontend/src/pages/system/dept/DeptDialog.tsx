import { useEffect, useMemo, useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { ApartmentOutlined } from '@ant-design/icons';
import { createDept, getDeptFormOptions, updateDept } from '../../../api/dept';
import type { Dept, DeptFormOptions, DeptRequest } from '../../../types/dept';
import { getApiErrorMessage } from '../../../utils/apiError';

interface DeptDialogProps {
  open: boolean;
  dept: Dept | null;
  allDepts: Dept[];
  onClose: () => void;
  onSaved: () => void;
}
interface DeptTreeNode { value: number; title: string; disabled?: boolean; children?: DeptTreeNode[] }
function toTree(depts: Dept[], parentId = 0, disabledIds: Set<number>): DeptTreeNode[] {
  return depts.filter((dept) => dept.parentId === parentId).map((dept) => ({ value: dept.id, title: dept.deptName, disabled: disabledIds.has(dept.id), children: toTree(depts, dept.id, disabledIds) }));
}
function blockedIds(depts: Dept[], id?: number) {
  const blocked = new Set<number>();
  if (!id) return blocked;
  const collect = (parentId: number) => depts.filter((dept) => dept.parentId === parentId).forEach((dept) => { blocked.add(dept.id); collect(dept.id); });
  blocked.add(id); collect(id);
  return blocked;
}

export function DeptDialog({ open, dept, allDepts, onClose, onSaved }: DeptDialogProps) {
  const [form] = Form.useForm<DeptRequest>();
  const { message } = App.useApp();
  const [options, setOptions] = useState<DeptFormOptions>();
  const [loadingOptions, setLoadingOptions] = useState(false);
  const editing = dept !== null;
  const parentTree = useMemo(() => toTree(allDepts, 0, blockedIds(allDepts, dept?.id)), [allDepts, dept?.id]);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(dept ? { parentId: dept.parentId || undefined, deptName: dept.deptName, deptCode: dept.deptCode, leaderUserId: dept.leaderUserId, sortOrder: dept.sortOrder, status: dept.status } : { parentId: undefined, deptName: '', deptCode: '', leaderUserId: undefined, sortOrder: 0, status: 1 });
    setLoadingOptions(true);
    void getDeptFormOptions().then(setOptions).catch((error) => message.error(getApiErrorMessage(error, '无法加载部门表单数据'))).finally(() => setLoadingOptions(false));
  }, [dept, form, message, open]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const response = editing ? await updateDept(dept.id, values) : await createDept(values);
      message.success(response.message || (editing ? '修改部门成功' : '新增部门成功'));
      onSaved(); onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存部门失败'));
    }
  };

  return <Modal className='system-dialog' open={open} width={680} title={<span className='system-dialog-title'><ApartmentOutlined />{editing ? '编辑部门' : '新增部门'}</span>} okText='确定' cancelText='取消' onCancel={onClose} onOk={() => void submit()} destroyOnHidden forceRender>
    {loadingOptions ? <div className='dialog-loading'><Spin /><span>正在加载表单数据...</span></div> : <Form form={form} layout='vertical' requiredMark={false}>
      <div className='form-grid'><Form.Item name='deptName' label='部门名称' rules={[{ required: true, message: '请输入部门名称' }]}><Input placeholder='例如：运营部' /></Form.Item><Form.Item name='deptCode' label='部门编码' rules={[{ required: true, message: '请输入部门编码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '以字母开头，可使用数字、下划线或短横线' }]}><Input placeholder='例如：OPERATIONS' /></Form.Item></div>
      <div className='form-grid'><Form.Item name='parentId' label='上级部门'><TreeSelect allowClear treeDefaultExpandAll treeData={parentTree} placeholder='根部门' /></Form.Item><Form.Item name='leaderUserId' label='负责人'><Select allowClear showSearch optionFilterProp='label' options={options?.leaders} placeholder='请选择负责人（可选）' /></Form.Item></div>
      <div className='form-grid'><Form.Item name='sortOrder' label='显示排序' rules={[{ required: true, message: '请输入排序值' }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item><Form.Item name='status' label='状态'><Radio.Group disabled={dept?.builtin === 1}><Radio value={1}>启用</Radio><Radio value={0}>停用</Radio></Radio.Group></Form.Item></div>
    </Form>}
  </Modal>;
}
