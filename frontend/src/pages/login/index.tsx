import { useState } from 'react';
import { App, Button, Checkbox, Form, Input } from 'antd';
import { EyeInvisibleOutlined, EyeTwoTone, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login, fetchMe } from '../../api/auth';
import { useAppDispatch } from '../../store/hooks';
import { setProfile, setSession } from '../../store/authSlice';
import { Logo } from '../../components/common/Logo';
import { getApiErrorMessage } from '../../utils/apiError';
import './login.css';
export default function LoginPage() {
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{ username: string; password: string; rememberMe: boolean }>();
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const onFinish = async (values: { username: string; password: string; rememberMe: boolean }) => {
    try {
      setSubmitting(true);
      const response = await login(values);
      const result = response.data;
      dispatch(setSession(result));
      const profile = await fetchMe();
      dispatch(setProfile(profile));
      const redirect = new URLSearchParams(location.search).get('redirect') ?? '/home';
      message.success(response.message || '登录成功');
      navigate(redirect, { replace: true });
    } catch (error) {
      const errorMessage = getApiErrorMessage(error, '登录失败，请检查系统服务');
      form.setFields([{ name: 'password', errors: [errorMessage] }]);
      message.error(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <main className='login-page'>
      <section className='login-hero'>
        <div className='orb orb-one' />
        <div className='orb orb-two' />
        <div className='hero-content'>
          <div className='hero-rule' />
          <h1>高效 · 安全 · 专业</h1>
          <p>构建更可靠的企业管理平台</p>
        </div>
        <footer>
          <span />
          让管理更简单　让企业更强大
        </footer>
      </section>
      <section className='login-panel'>
        <div className='login-form-wrap'>
          <Logo />
          <div className='login-heading'>
            <h2>欢迎登录</h2>
            <p>请输入账号信息以继续访问系统</p>
          </div>
          <Form form={form} layout='vertical' initialValues={{ rememberMe: true, username: 'admin', password: 'Admin@123456' }} onFinish={onFinish} requiredMark={false}>
            <Form.Item name='username' rules={[{ required: true, message: '请输入用户名' }]}>
              <Input size='large' prefix={<UserOutlined />} placeholder='请输入用户名' autoComplete='username' />
            </Form.Item>
            <Form.Item name='password' rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password
                size='large'
                prefix={<LockOutlined />}
                placeholder='请输入密码'
                iconRender={(visible) => (visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />)}
                autoComplete='current-password'
              />
            </Form.Item>
            <div className='login-options'>
              <Form.Item name='rememberMe' valuePropName='checked' noStyle>
                <Checkbox>记住我</Checkbox>
              </Form.Item>
              <span>
                <Link to='/forgot'>忘记密码</Link>
                <i /> <Link to='/register'>用户注册</Link>
              </span>
            </div>
            <Button htmlType='submit' size='large' type='primary' block loading={submitting}>
              登录
            </Button>
            <Button size='large' block className='sso-button'>
              统一认证登录
            </Button>
          </Form>
          <div className='login-copyright'>© 2026 Kariya Admin　版权所有</div>
        </div>
      </section>
    </main>
  );
}
