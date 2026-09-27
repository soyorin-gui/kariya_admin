import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { LockOutlined, MailOutlined, PhoneOutlined, UserOutlined } from '@ant-design/icons';
import { createUser, checkUsernameAvailable, getUser, getUserFormOptions, updateUser } from '../../../api/user';
import type { DepartmentOption, User, UserCreateRequest, UserFormOptions, UserRequest } from '../../../types/user';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../../../components/FieldLabel';
import { PASSWORD_MESSAGE, PASSWORD_PATTERN } from '../../../utils/passwordPolicy';

interface UserDialogProps {
  open: boolean;
  userId: number | null;
  onClose: () => void;
  onSaved: () => void;
}
type UserFormValues = UserRequest & Partial<Pick<UserCreateRequest, 'password' | 'confirmPassword'>>;
const emptyUser: Partial<UserFormValues> = { username: '', realName: '', phone: '', email: '', deptId: undefined, roleIds: [], status: 1, password: '', confirmPassword: '' };
interface DepartmentTreeNode { value: number; title: string; disabled?: boolean; children?: DepartmentTreeNode[] }

function toDepartmentTree(options: DepartmentOption[]): DepartmentTreeNode[] {
  const ids = new Set(options.map((option) => option.value));
  const children = new Map<number, DepartmentOption[]>();
  options.forEach((option) => {
    const parentId = option.parentId && ids.has(option.parentId) ? option.parentId : 0;
    children.set(parentId, [...(children.get(parentId) ?? []), option]);
  });
  const build = (parentId: number): DepartmentTreeNode[] => (children.get(parentId) ?? []).map((option) => ({
    value: option.value,
    title: option.label,
    disabled: option.disabled,
    children: build(option.value),
  }));
  return build(0);
}

export function UserDialog({ open, userId, onClose, onSaved }: UserDialogProps) {
  const [form] = Form.useForm<UserFormValues>();
  const { message } = App.useApp();
  const [options, setOptions] = useState<UserFormOptions>();
  const [user, setUser] = useState<User>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const editing = userId !== null;
  /**
   * 该用户"没有部门"（自助注册 / 外部身份开户创建的账号 dept_id 就是 NULL），
   * 而不是"原部门被停用了"。两者在界面上必须区分对待，见下面 departmentOptions 的说明。
   */
  const needsDepartment = Boolean(user && user.deptId == null);
  const departmentOptions = useMemo(() => {
    const available = options?.departments ?? [];
    if (!user) return available;
    // dept_id 为 NULL 时不能走下面那条"原部门已停用"的分支：
    //   1) 后端 UserVO 在没有部门时 deptName 给的是 "-"，于是会拼出一个
    //      「-（当前所属部门，已停用）」的假节点，看起来像数据坏了；
    //   2) 更要紧的是那句话是**假的** —— 这个用户压根没有部门，
    //      管理员会跑去部门管理里找一个根本不存在的"已停用部门"。
    //   3) 该节点还是 disabled 的，选不了，唯一效果就是制造视觉噪音 + 讲错故事。
    if (user.deptId == null) return available;
    if (available.some((option) => option.value === user.deptId)) return available;
    return [{ value: user.deptId, label: `${user.deptName}（当前所属部门，已停用）`, disabled: true }, ...available];
  }, [options?.departments, user]);
  const departmentTree = useMemo(() => toDepartmentTree(departmentOptions), [departmentOptions]);

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;
    form.resetFields();
    setUser(undefined);
    setOptions(undefined);
    setLoading(true);
    const load = async () => {
      try {
        const [detail, value] = await Promise.all([
          userId === null ? Promise.resolve(undefined) : getUser(userId),
          getUserFormOptions(),
        ]);
        if (!active) return;
        setUser(detail);
        setOptions(value);
        if (detail) {
          // 注册用户/内部用户是后端自动维护的基础身份，不在普通管理员的候选角色里。
          // 表单只提交当前管理员有权调整的业务角色，后端会原样保留基础身份。
          const assignable = new Set(value.roles.map((role) => role.value));
          form.setFieldsValue({ username: detail.username, realName: detail.realName, phone: detail.phone, email: detail.email,
            deptId: detail.deptId ?? undefined, roleIds: detail.roleIds.filter((roleId) => assignable.has(roleId)), status: detail.status });
        } else {
          form.setFieldsValue(emptyUser);
        }
      } catch (error) {
        if (!active) return;
        message.error(getApiErrorMessage(error, editing ? '无法加载用户详情' : '无法加载部门与角色数据'));
        onClose();
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => { active = false; };
  }, [editing, form, message, open, userId]);

  /**
   * 用户名重名校验：在输入阶段就给出提示，而不是等到提交后弹一个错误。
   *
   * 两点取舍：
   * 1) 校验接口本身失败（网络抖动、无 system:user:add 权限）时直接放行，不阻塞用户——
   *    表单校验不该因为一个辅助接口不可用就让人填不下去，后端提交时仍会做最终校验。
   * 2) 编辑模式下用户名不可改（Input disabled），所以不做校验，避免白打一次接口。
   */
  const validateUsername = async (_rule: unknown, value: string) => {
    const username = String(value ?? '').trim();
    if (!username || editing) return;
    let availability: Awaited<ReturnType<typeof checkUsernameAvailable>>;
    try {
      availability = await checkUsernameAvailable(username);
    } catch {
      return;
    }
    if (!availability.available) throw new Error(availability.message || '该用户名不可用');
  };

  /**
   * 防重复提交：ref 是同步闸门，state 只负责按钮的加载效果。
   * 这里的窗口尤其大 —— 用户名字段带 validateDebounce={500}，校验器里还会请求
   * username-available，整个校验期间按钮都是可点的；以前正是"先 await 校验再置位"，
   * 双击会发出两次创建请求（第二条撞 username 唯一索引），用户先看到"新增用户成功"
   * 再看到"该用户名已被使用"，无从判断到底建没建成。只用 state 仍有极短的竞态窗口，
   * 因为 React 完成下一次渲染前，两次事件都可能读到旧值。
   */
  const submit = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const response = editing
        ? await updateUser(userId!, values)
        : await createUser({ ...values, password: values.password!, confirmPassword: values.confirmPassword! });
      message.success(response.message || (editing ? '修改用户成功' : '新增用户成功'));
      onSaved();
      onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存失败，请稍后重试'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='user-dialog system-dialog'
      open={open}
      width={660}
      title={editing ? '编辑用户' : '新增用户'}
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
      {loading ? (
        <div className='dialog-loading'>
          <Spin />
          <span>正在加载表单数据...</span>
        </div>
      ) : (
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
          <Form.Item
            name='username'
            label={<FieldLabel text='用户名' hint='登录账号，创建后不可修改。占用过（含已删除用户）的名字无法重复使用。' />}
            hasFeedback
            // 输入停顿 500ms 才真正发请求，避免每敲一个字符就打一次校验接口。
            validateDebounce={500}
            rules={[{ required: true, message: '请输入用户名' }, { validator: validateUsername }]}
          >
            <Input disabled={editing} prefix={<UserOutlined />} placeholder='请输入用户名' />
          </Form.Item>
          <Form.Item name='realName' label='姓名' rules={[{ required: true, message: '请输入姓名' }, { max: 64, message: '姓名最长 64 个字符' }]}>
            <Input prefix={<UserOutlined />} placeholder='请输入姓名' />
          </Form.Item>
          {/*
            手机号规则与后端 UserRequest 保持一致（^\+?[0-9 ()-]{5,32}$）。
            这里刻意不做严格的 11 位中国大陆号码校验：座机、分机、境外号码都会被误伤，
            而那属于业务规则，不该由通用校验层决定。
          */}
          <Form.Item
            name='phone'
            label='手机号'
            rules={[{ required: true, message: '请输入手机号' }, { pattern: /^\+?[0-9 ()-]{5,32}$/, message: '手机号只能包含数字、空格、括号和短横线（5-32 位）' }]}
          >
            <Input prefix={<PhoneOutlined />} placeholder='请输入手机号' />
          </Form.Item>
          <Form.Item name='email' label='邮箱' rules={[{ type: 'email', message: '邮箱格式不正确' }, { max: 128, message: '邮箱最长 128 个字符' }]}>
            <Input prefix={<MailOutlined />} placeholder='请输入邮箱地址（可选）' />
          </Form.Item>
          {!editing && <Form.Item name='password' label='登录密码' rules={[{ required: true, message: '请输入密码' }, { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete='new-password' />
          </Form.Item>}
          {!editing && <Form.Item name='confirmPassword' label='确认密码' dependencies={['password']} rules={[{ required: true, message: '请确认密码' },
            ({ getFieldValue }) => ({ validator(_, value) { return !value || value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}>
            <Input.Password prefix={<LockOutlined />} autoComplete='new-password' />
          </Form.Item>}
          <Form.Item
            name='deptId'
            label={<FieldLabel text='所属部门' hint={needsDepartment
              ? '该账号由自助注册创建，目前没有所属部门。必须为它指定一个部门才能保存。'
              : '停用部门不接收新用户；原本就在停用部门中的用户仍可修改资料，或迁往下列启用部门。可选范围同时受你自己的数据范围限制。'} />}
            rules={[{ required: true, message: needsDepartment ? '该账号尚未分配部门，请先为其指定部门' : '请选择部门' }]}
          >
            <TreeSelect
              showSearch
              treeNodeFilterProp='title'
              treeDefaultExpandAll
              treeData={departmentTree}
              placeholder='请选择或搜索部门'
            />
          </Form.Item>
          <Form.Item name='roleIds' label={<FieldLabel text='角色' hint='至少一个。候选里只会出现你本人有权分配的角色（权限不高于你自己的）。' />} rules={[{ required: true, message: '请至少选择一个角色' }]}>
            <Select mode='multiple' showSearch optionFilterProp='label' maxTagCount='responsive' placeholder='请选择或搜索角色' options={options?.roles} />
          </Form.Item>
          <Form.Item name='status' label='状态' rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value={1}>启用</Radio>
              <Radio value={0}>禁用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
