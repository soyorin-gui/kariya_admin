import { useEffect, useState } from 'react';
import { App, Button, Card, Form, Input, Modal, Space, Typography } from 'antd';
import { LinkOutlined, LogoutOutlined, PlusCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { bindOnboardingAccount, createOnboardingAccount, fetchMe, logout } from '../../../api/auth';
import { useAppDispatch, useAppSelector } from '../../../store/hooks';
import { clearSession, setProfile, setSession } from '../../../store/authSlice';
import { firstAvailablePath } from '../../../router/menu';
import { getApiErrorMessage } from '../../../utils/apiError';
import { PASSWORD_MESSAGE, PASSWORD_PATTERN } from '../../../utils/passwordPolicy';
import '../../system/shared.css';
import './setup.css';

export default function AccountSetupPage() {
  const onboarding = useAppSelector((state) => state.auth.onboarding);
  const principalType = useAppSelector((state) => state.auth.principalType);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [mode, setMode] = useState<'bind' | 'create' | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [bindForm] = Form.useForm<{ username: string; password: string }>();
  const [createForm] = Form.useForm<{ username: string; realName: string; phone?: string; email?: string; password: string; confirmPassword: string }>();

  useEffect(() => {
    if (principalType === 'MEMBER') navigate('/account/security', { replace: true });
  }, [navigate, principalType]);

  const complete = async (accessToken: string, success: string) => {
    const profile = await fetchMe();
    dispatch(setSession({ accessToken, principalType: profile.principalType, user: profile.user }));
    dispatch(setProfile(profile));
    message.success(success);
    navigate(firstAvailablePath(profile.menus) ?? '/account/security', { replace: true });
  };
  const bind = async () => {
    try {
      setSubmitting(true);
      const values = await bindForm.validateFields();
      const result = await bindOnboardingAccount(values);
      await complete(result.data.accessToken, '已绑定到原有账号');
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '绑定账号失败'));
    } finally {
      setSubmitting(false);
    }
  };
  const create = async () => {
    try {
      setSubmitting(true);
      const values = await createForm.validateFields();
      const result = await createOnboardingAccount(values);
      await complete(result.data.accessToken, '账号创建成功');
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '创建账号失败'));
    } finally {
      setSubmitting(false);
    }
  };
  const exit = async () => {
    try {
      await logout();
    } finally {
      dispatch(clearSession());
      navigate('/login', { replace: true });
    }
  };

  return (
    <main className='account-setup-page'>
      <div className='account-setup-orb account-setup-orb-one' />
      <div className='account-setup-orb account-setup-orb-two' />
      <section className='account-setup-shell'>
        <div className='account-setup-badge'>
          <SafetyCertificateOutlined /> 外部身份已验证
        </div>
        <Typography.Title level={1}>完成系统账号设置</Typography.Title>
        <Typography.Paragraph className='account-setup-lead'>
          你已通过 {onboarding?.providerKey || '外部平台'} 完成身份验证。必须创建新账号或绑定已有账号后才能进入系统；退出将返回登录页。
        </Typography.Paragraph>
        <div className='account-setup-identity'>
          {onboarding?.displayName && <span>身份名称　{onboarding.displayName}</span>}
          {onboarding?.email && <span>邮箱　{onboarding.email}</span>}
          {onboarding?.employeeNo && <span>工号　{onboarding.employeeNo}</span>}
        </div>
        <div className='account-setup-actions'>
          <Card hoverable onClick={() => setMode('bind')}>
            <LinkOutlined />
            <strong>绑定已有账号</strong>
            <p>验证系统用户名和密码，把当前外部身份绑定到该账号。</p>
          </Card>
          <Card hoverable onClick={() => setMode('create')}>
            <PlusCircleOutlined />
            <strong>创建新账号</strong>
            <p>填写开户资料并创建密码，账号默认获得基础角色和仅本人数据权限。</p>
          </Card>
        </div>
        <Space>
          <Button type='primary' onClick={() => setMode('create')}>
            创建账号
          </Button>
          <Button onClick={() => setMode('bind')}>绑定已有账号</Button>
          <Button type='text' icon={<LogoutOutlined />} onClick={() => void exit()}>
            退出
          </Button>
        </Space>
      </section>
      <Modal
        className='system-dialog'
        width={560}
        title={<div className='system-dialog-title'>绑定已有账号</div>}
        open={mode === 'bind'}
        onCancel={() => setMode(null)}
        onOk={() => void bind()}
        confirmLoading={submitting}
        okText='确认绑定'
        destroyOnHidden
      >
        <Form form={bindForm} labelCol={{ flex: '96px' }} wrapperCol={{ flex: 1 }} labelWrap colon={false} requiredMark={false}>
          <Form.Item name='username' label='用户名' rules={[{ required: true, message: '请输入用户名' }]}>
            <Input autoComplete='username' />
          </Form.Item>
          <Form.Item name='password' label='密码' rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete='current-password' />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        className='system-dialog'
        width={600}
        title={<div className='system-dialog-title'>创建系统账号</div>}
        open={mode === 'create'}
        onCancel={() => setMode(null)}
        onOk={() => void create()}
        confirmLoading={submitting}
        okText='创建账号'
        destroyOnHidden
      >
        <Form
          form={createForm}
          initialValues={{ realName: onboarding?.displayName, email: onboarding?.email, username: onboarding?.employeeNo }}
          labelCol={{ flex: '96px' }}
          wrapperCol={{ flex: 1 }}
          labelWrap
          colon={false}
          requiredMark={false}
        >
          <Form.Item
            name='username'
            label='用户名'
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[A-Za-z0-9_.-]+$/, message: '用户名格式不正确' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item name='realName' label='姓名' rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name='phone' label='手机号'>
            <Input placeholder='可选' />
          </Form.Item>
          <Form.Item name='email' label='联系邮箱' rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input placeholder='可选' />
          </Form.Item>
          <Form.Item
            name='password'
            label='登录密码'
            rules={[
              { required: true, message: '请输入密码' },
              { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE },
            ]}
          >
            <Input.Password autoComplete='new-password' />
          </Form.Item>
          <Form.Item
            name='confirmPassword'
            label='确认密码'
            dependencies={['password']}
            rules={[
              { required: true, message: '请确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete='new-password' />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  );
}
