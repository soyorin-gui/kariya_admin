import { useLayoutEffect, useRef, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin } from 'antd';
import { createRole, getRole, updateRole } from '../../../api/role';
import type { Role, RoleRequest } from '../../../types/role';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../../../components/FieldLabel';

interface RoleDialogProps {
  open: boolean;
  roleId: number | null;
  onClose: () => void;
  onSaved: () => void;
}

const dataScopeOptions = [
  { value: 'ALL', label: '全部数据权限' },
  { value: 'DEPT_AND_CHILDREN', label: '本部门及下级' },
  { value: 'DEPT', label: '仅本部门数据' },
  { value: 'SELF', label: '仅本人数据' },
];

export function RoleDialog({ open, roleId, onClose, onSaved }: RoleDialogProps) {
  const [form] = Form.useForm<RoleRequest>();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [role, setRole] = useState<Role>();
  const submittingRef = useRef(false);
  const editing = roleId !== null;

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;
    form.resetFields();
    setRole(undefined);
    setLoading(false);
    if (roleId === null) {
      form.setFieldsValue({ roleName: '', roleCode: '', dataScope: 'SELF', status: 1 });
      return;
    }
    setLoading(true);
    void getRole(roleId).then((detail) => {
      if (!active) return;
      setRole(detail);
      form.setFieldsValue({ roleName: detail.roleName, roleCode: detail.roleCode, dataScope: detail.dataScope, status: detail.status });
    }).catch((error) => {
      if (!active) return;
      message.error(getApiErrorMessage(error, '无法加载角色详情'));
      onClose();
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [form, message, open, roleId]);

  /**
   * 防重复提交：ref 同步挡住同一轮渲染内的第二次调用，state 负责按钮加载效果。
   */
  const submit = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const response = editing ? await updateRole(roleId!, values) : await createRole(values);
      message.success(response.message || (editing ? '修改角色成功' : '新增角色成功'));
      onSaved();
      onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存角色失败'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='system-dialog'
      open={open}
      width={600}
      title={editing ? '编辑角色' : '新增角色'}
      okText='确定'
      cancelText='取消'
      onCancel={onClose}
      onOk={() => void submit()}
      confirmLoading={submitting}
      okButtonProps={{ disabled: loading }}
      cancelButtonProps={{ disabled: submitting || loading }}
      closable={!submitting && !loading}
      maskClosable={!submitting && !loading}
      keyboard={!submitting && !loading}
      destroyOnHidden
      forceRender
    >
      {loading ? <div className='dialog-loading'><Spin /><span>正在加载角色详情...</span></div> : <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
        <Form.Item name='roleName' label='角色名称' rules={[{ required: true, message: '请输入角色名称' }, { max: 80, message: '角色名称最长 80 个字符' }]}>
          <Input placeholder='例如：运营专员' />
        </Form.Item>
        <Form.Item
          name='roleCode'
          label={<FieldLabel text='角色标识' hint='角色的唯一英文标识，只能小写字母开头，用于后端判断角色，创建后不建议再改；删除角色后该标识仍会被历史记录占用。' />}
          rules={[{ required: true, message: '请输入角色标识' }, { max: 80, message: '角色标识最长 80 个字符' }, { pattern: /^[a-z][a-z0-9_:.-]*$/, message: '使用小写字母开头，可包含数字、冒号、下划线、点和短横线' }]}
        >
          <Input disabled={role?.builtin === 1} placeholder='例如：operator' />
        </Form.Item>
        <Form.Item name='dataScope' label={<FieldLabel text='数据范围' hint='决定该角色能看到哪些部门的数据：全部=不限；本部门及下级=自己部门加所有子部门；仅本部门=只自己部门；仅本人=只能看自己。' />} rules={[{ required: true, message: '请选择数据权限范围' }]}>
          <Select options={dataScopeOptions} />
        </Form.Item>
        <Form.Item name='status' label='状态' rules={[{ required: true }]}>
          <Radio.Group disabled={role?.builtin === 1}>
            <Radio value={1}>启用</Radio>
            <Radio value={0}>禁用</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>}
    </Modal>
  );
}
