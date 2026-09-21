import { useEffect, useState } from 'react';
import { App, Avatar, Form, Input, Modal, Radio, Select, Spin } from 'antd';
import { LockOutlined, MailOutlined, PhoneOutlined, UserAddOutlined, UserOutlined } from '@ant-design/icons';
import { createUser, getUserFormOptions, updateUser } from '../../../api/user';
import type { User, UserFormOptions, UserRequest } from '../../../types/user';
import { getApiErrorMessage } from '../../../utils/apiError';

interface UserDialogProps {
  open: boolean;
  user: User | null;
  onClose: () => void;
  onSaved: () => void;
}
const emptyUser: Partial<UserRequest> = { username: '', realName: '', phone: '', email: '', deptId: undefined, roleIds: [], status: 1 };

export function UserDialog({ open, user, onClose, onSaved }: UserDialogProps) {
  const [form] = Form.useForm<UserRequest>();
  const { message } = App.useApp();
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

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const response = editing ? await updateUser(user.id, values) : await createUser(values);
      message.success(response.message || (editing ? '修改用户成功' : '新增用户成功'));
      onSaved();
      onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存失败，请稍后重试'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='user-dialog'
      open={open}
      width={760}
      title={
        <span className='user-dialog-title'>
          <Avatar icon={<UserAddOutlined />} />
          {editing ? '编辑用户' : '新增用户'}
        </span>
      }
      okText='确定'
      cancelText='取消'
      onCancel={onClose}
      onOk={() => void submit()}
      confirmLoading={submitting}
      destroyOnHidden
      forceRender
      styles={{ body: { maxHeight: '60vh', overflowY: 'auto', paddingRight: 8 } }}
    >
      {loadingOptions ? (
        <div className='dialog-loading'>
          <Spin />
          <span>正在加载表单数据...</span>
        </div>
      ) : (
        <Form form={form} layout='vertical' requiredMark={false}>
          <div className='form-grid'>
            <Form.Item name='username' label='用户名' rules={[{ required: true, message: '请输入用户名' }]}>
              <Input disabled={editing} prefix={<UserOutlined />} placeholder='请输入用户名' />
            </Form.Item>
            <Form.Item name='realName' label='姓名' rules={[{ required: true, message: '请输入姓名' }]}>
              <Input prefix={<UserOutlined />} placeholder='请输入姓名' />
            </Form.Item>
          </div>
          <div className='form-grid'>
            <Form.Item name='phone' label='手机号' rules={[{ required: true, message: '请输入手机号' }]}>
              <Input prefix={<PhoneOutlined />} placeholder='请输入手机号' />
            </Form.Item>
            <Form.Item name='email' label='邮箱' rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
              <Input prefix={<MailOutlined />} placeholder='请输入邮箱地址（可选）' />
            </Form.Item>
          </div>
          <div className='form-grid'>
            <Form.Item name='deptId' label='所属部门' rules={[{ required: true, message: '请选择部门' }]}>
              <Select placeholder='请选择部门' options={options?.departments} />
            </Form.Item>
            <Form.Item name='roleIds' label='角色' rules={[{ required: true, message: '请至少选择一个角色' }]}>
              <Select mode='multiple' placeholder='请选择角色' options={options?.roles} />
            </Form.Item>
          </div>
          <Form.Item name='status' label='状态' rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value={1}>启用</Radio>
              <Radio value={0}>禁用</Radio>
            </Radio.Group>
          </Form.Item>
          {!editing && (
            <div className='password-note'>
              <LockOutlined />
              初始密码为 <strong>Admin@123456</strong>，请提示用户首次登录后修改。
            </div>
          )}
        </Form>
      )}
    </Modal>
  );
}
