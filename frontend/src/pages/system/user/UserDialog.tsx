import { useEffect, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin } from 'antd';
import { LockOutlined, MailOutlined, PhoneOutlined, UserOutlined } from '@ant-design/icons';
import { createUser, checkUsernameAvailable, getUserFormOptions, updateUser } from '../../../api/user';
import type { User, UserFormOptions, UserRequest } from '../../../types/user';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../FieldLabel';

interface UserDialogProps {
  open: boolean;
  user: User | null;
  onClose: () => void;
  onSaved: () => void;
}
const emptyUser: Partial<UserRequest> = { username: '', realName: '', phone: '', email: '', deptId: undefined, roleIds: [], status: 1 };

export function UserDialog({ open, user, onClose, onSaved }: UserDialogProps) {
  const [form] = Form.useForm<UserRequest>();
  const { message, modal } = App.useApp();
  const [options, setOptions] = useState<UserFormOptions>();
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const editing = user !== null;

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(user ? { username: user.username, realName: user.realName, phone: user.phone, email: user.email, deptId: user.deptId, roleIds: user.roleIds, status: user.status } : emptyUser);
    setLoadingOptions(true);
    void getUserFormOptions()
      .then(setOptions)
      .catch((error) => message.error(getApiErrorMessage(error, '无法加载部门与角色数据')))
      .finally(() => setLoadingOptions(false));
  }, [form, message, open, user]);

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

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const response = editing ? await updateUser(user.id, values) : await createUser(values);
      message.success(response.message || (editing ? '修改用户成功' : '新增用户成功'));
      onSaved();
      onClose();
      if (!editing) {
        const temporaryPassword = 'temporaryPassword' in response.data ? response.data.temporaryPassword : '';
        modal.info({ title: '用户初始密码', content: <p>请安全地告知用户初始密码：<strong>{temporaryPassword}</strong>。该密码只显示一次，首次登录后必须修改。</p> });
      }
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存失败，请稍后重试'));
    } finally {
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
          <Form.Item name='realName' label='姓名' rules={[{ required: true, message: '请输入姓名' }]}>
            <Input prefix={<UserOutlined />} placeholder='请输入姓名' />
          </Form.Item>
          <Form.Item name='phone' label='手机号' rules={[{ required: true, message: '请输入手机号' }]}>
            <Input prefix={<PhoneOutlined />} placeholder='请输入手机号' />
          </Form.Item>
          <Form.Item name='email' label='邮箱' rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input prefix={<MailOutlined />} placeholder='请输入邮箱地址（可选）' />
          </Form.Item>
          <Form.Item name='deptId' label={<FieldLabel text='所属部门' hint='可选范围受你自己的数据范围限制；它决定该用户在"本部门"类数据范围里算哪个部门。' />} rules={[{ required: true, message: '请选择部门' }]}>
            <Select placeholder='请选择部门' options={options?.departments} />
          </Form.Item>
          <Form.Item name='roleIds' label={<FieldLabel text='角色' hint='至少一个。候选里只会出现你本人有权分配的角色（权限不高于你自己的）。' />} rules={[{ required: true, message: '请至少选择一个角色' }]}>
            <Select mode='multiple' placeholder='请选择角色' options={options?.roles} />
          </Form.Item>
          <Form.Item name='status' label='状态' rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value={1}>启用</Radio>
              <Radio value={0}>禁用</Radio>
            </Radio.Group>
          </Form.Item>
          {!editing && (
            <div className='password-note'>
              <LockOutlined />
              系统会生成一次性初始密码，保存后显示。
            </div>
          )}
        </Form>
      )}
    </Modal>
  );
}
