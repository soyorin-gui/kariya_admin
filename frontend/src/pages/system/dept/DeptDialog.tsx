import { useEffect, useMemo, useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { createDept, getDeptFormOptions, updateDept } from '../../../api/dept';
import type { Dept, DeptFormOptions, DeptRequest } from '../../../types/dept';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../FieldLabel';

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

  return (
    <Modal
      className='system-dialog'
      open={open}
      width={600}
      title={editing ? '编辑部门' : '新增部门'}
      okText='确定'
      cancelText='取消'
      onCancel={onClose}
      onOk={() => void submit()}
      destroyOnHidden
      forceRender
    >
      {loadingOptions ? (
        <div className='dialog-loading'>
          <Spin />
          <span>正在加载表单数据...</span>
        </div>
      ) : (
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
          <Form.Item name='deptName' label='部门名称' rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder='例如：运营部' />
          </Form.Item>
          <Form.Item
            name='deptCode'
            label={<FieldLabel text='部门编码' hint='部门的唯一英文编码，以字母开头，可使用数字、下划线或短横线；删除后该编码仍会被历史记录占用。' />}
            rules={[{ required: true, message: '请输入部门编码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '以字母开头，可使用数字、下划线或短横线' }]}
          >
            <Input placeholder='例如：OPERATIONS' />
          </Form.Item>
          <Form.Item name='parentId' label={<FieldLabel text='上级部门' hint='留空即为根部门。已删除的部门不在候选里；不能选择自身或自身的下级。' />}>
            <TreeSelect allowClear treeDefaultExpandAll treeData={parentTree} placeholder='根部门' />
          </Form.Item>
          <Form.Item name='leaderUserId' label={<FieldLabel text='负责人' hint='该部门的负责人，仅用于展示与后续流程找人，不影响权限。' />}>
            <Select allowClear showSearch optionFilterProp='label' options={options?.leaders} placeholder='请选择负责人（可选）' />
          </Form.Item>
          <Form.Item name='sortOrder' label={<FieldLabel text='显示排序' hint='数字越小越靠前。只能填 0 及以上的整数。' />} rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} step={1} precision={0} style={{ width: '100%' }} placeholder='例如：10' />
          </Form.Item>
          <Form.Item name='status' label='状态' rules={[{ required: true }]}>
            <Radio.Group disabled={dept?.builtin === 1}>
              <Radio value={1}>启用</Radio>
              <Radio value={0}>停用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
