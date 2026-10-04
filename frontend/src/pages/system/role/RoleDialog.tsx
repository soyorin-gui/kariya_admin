import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { createRole, getRole, updateRole, getGrantableDepartments } from '../../../api/role';
import type { Role, RoleRequest, RoleDeptOption } from '../../../types/role';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../../../components/FieldLabel';

interface RoleDialogProps {
  open: boolean;
  roleId: number | null;
  onClose: () => void;
  onSaved: () => void;
}

interface DepartmentNode { value: number; title: string; disableCheckbox: boolean; children: DepartmentNode[] }

const dataScopeOptions = [
  { value: 'ALL', label: '全部数据权限' },
  { value: 'DEPT_AND_CHILDREN', label: '本部门及下级' },
  { value: 'DEPT', label: '仅本部门数据' },
  { value: 'SELF', label: '仅本人数据' },
  { value: 'CUSTOM', label: '指定部门' },
];

export function RoleDialog({ open, roleId, onClose, onSaved }: RoleDialogProps) {
  const [form] = Form.useForm<RoleRequest>();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [role, setRole] = useState<Role>();
  const submittingRef = useRef(false);
  const editing = roleId !== null;
  const dataScope = Form.useWatch('dataScope', form);
  const [departments, setDepartments] = useState<RoleDeptOption[]>([]);
  const departmentTree = useMemo(() => {
    const ids = new Set(departments.map((dept) => dept.id));
    const build = (parentId: number): DepartmentNode[] =>
      departments.filter((dept) => (ids.has(dept.parentId) ? dept.parentId : 0) === parentId).map((dept) => ({
        value: dept.id,
        title: dept.deptName + (dept.status === 0 ? '（停用）' : ''),
        disableCheckbox: !dept.selectable,
        children: build(dept.id),
      }));
    return build(0);
  }, [departments]);

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;
    form.resetFields();
    setRole(undefined);
    setLoading(false);
    setDepartments([]);
    setLoading(true);
    void Promise.all([getGrantableDepartments(), roleId === null ? Promise.resolve(undefined) : getRole(roleId)])
      .then(([options, detail]) => {
        if (!active) return;
        setDepartments(options);
        setRole(detail);
        form.setFieldsValue(detail
          ? { roleName: detail.roleName, roleCode: detail.roleCode, dataScope: detail.dataScope, status: detail.status, customDeptIds: detail.customDeptIds }
          : { roleName: '', roleCode: '', dataScope: 'SELF', status: 1, customDeptIds: [] });
      }).catch((error) => {
        if (!active) return;
        message.error(getApiErrorMessage(error, '无法加载角色配置'));
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
      const payload = { ...values, customDeptIds: values.dataScope === 'CUSTOM' ? values.customDeptIds : [] };
      const response = editing ? await updateRole(roleId!, payload) : await createRole(payload);
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
        <Form.Item name='dataScope' label={<FieldLabel text='数据范围' hint='限制该角色授予的各项操作的数据范围。指定部门只包含明确选中的部门，不自动包含下级。' />} rules={[{ required: true, message: '请选择数据权限范围' }]}>
          <Select options={dataScopeOptions} disabled={role?.scopeEditable === false} />
        </Form.Item>
        {dataScope === 'CUSTOM' && <Form.Item
          name='customDeptIds'
          getValueFromEvent={(values: { value: number }[]) => values.map((value) => value.value)}
          getValueProps={(values: number[] = []) => ({ value: values.map((id) => ({ value: id, label: departments.find((dept) => dept.id === id)?.deptName ?? `部门 ${id}` })) })}
          label={<FieldLabel text='指定部门' hint='父子部门独立选择；只读祖先仅用于展示层级。停用部门的既有数据仍可授权。' />}
          extra='仅包含所选部门；切换为其他范围并保存后，将清除指定部门配置。'
          rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个指定部门' }]}
        >
          <TreeSelect
            treeData={departmentTree}
            treeCheckable
            treeCheckStrictly
            labelInValue
            treeDefaultExpandAll
            showSearch
            treeNodeFilterProp='title'
            placeholder='请选择或搜索部门'
            disabled={role?.scopeEditable === false}
            maxTagCount='responsive'
            allowClear
          />
        </Form.Item>}
        {role?.scopeEditable === false && <Form.Item label='说明'>已分配角色的数据范围与状态仅超级管理员可修改。</Form.Item>}
        <Form.Item name='status' label='状态' rules={[{ required: true }]}>
          <Radio.Group disabled={role?.builtin === 1 || role?.scopeEditable === false}>
            <Radio value={1}>启用</Radio>
            <Radio value={0}>禁用</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>}
    </Modal>
  );
}
