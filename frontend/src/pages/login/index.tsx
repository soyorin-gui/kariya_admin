import { useEffect, useRef, useState, type ReactNode } from 'react';
import { App, Button, Checkbox, Form, Input, Tooltip } from 'antd';
import { EyeInvisibleOutlined, EyeTwoTone, GithubOutlined, GoogleOutlined, LockOutlined, SafetyCertificateOutlined, UserOutlined, WechatOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login, fetchMe, getExternalProviders } from '../../api/auth';
import type { ExternalProvider } from '../../types/auth';
import { useAppDispatch } from '../../store/hooks';
import { setProfile, setSession } from '../../store/authSlice';
import { LoginBrand } from './LoginBrand';
import { firstAvailablePath, resolveRedirect } from '../../router/menu';
import { getApiErrorMessage } from '../../utils/apiError';
import './login.css';

const providerIcons: Record<string, ReactNode> = {
  wechat: <WechatOutlined />,
  github: <GithubOutlined />,
  google: <GoogleOutlined />,
  enterprise: <SafetyCertificateOutlined />,
};
const apiUrl = (path: string) => `${(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')}${path}`;

export default function LoginPage() {
  const [submitting, setSubmitting] = useState(false);
  const submitLocked = useRef(false);
  const [providers, setProviders] = useState<ExternalProvider[]>([]);
  const [form] = Form.useForm<{ username: string; password: string; rememberMe: boolean }>();
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(() => {
    void getExternalProviders()
      .then(setProviders)
      .catch(() => setProviders([]));
  }, []);
  const onFinish = async (values: { username: string; password: string; rememberMe: boolean }) => {
    if (submitLocked.current) return;
    submitLocked.current = true;
    try {
      setSubmitting(true);
      const response = await login(values);
      const result = response.data;
      const profile = await fetchMe();
      dispatch(setSession({ accessToken: result.accessToken, principalType: profile.principalType, user: profile.user, onboarding: profile.onboarding }));
      dispatch(setProfile(profile));
      // 没有显式 redirect 时落到「我的第一项可见页面」：菜单是按角色授权的，
      // 写死 /home 会让没被授予首页的用户一登录就撞 403。
      const redirect = resolveRedirect(new URLSearchParams(location.search).get('redirect'), window.location.origin) ?? firstAvailablePath(profile.menus);
      // 一个可见页面都没有时给出明确兜底，避免带着 undefined 去 navigate。
      message.success(response.message || '登录成功');
      navigate(redirect ?? '/404', { replace: true });
    } catch (error) {
      const errorMessage = getApiErrorMessage(error, '登录失败，请检查系统服务');
      form.setFields([{ name: 'password', errors: [errorMessage] }]);
      message.error(errorMessage);
    } finally {
      submitLocked.current = false;
      setSubmitting(false);
    }
  };
  const onThirdParty = (provider: ExternalProvider) => {
    if (!provider.enabled) {
      message.info(`${provider.displayName}登录尚未配置`);
      return;
    }
    const redirect = resolveRedirect(new URLSearchParams(location.search).get('redirect'), window.location.origin) ?? '/';
    const rememberMe = Boolean(form.getFieldValue('rememberMe'));
    window.location.assign(apiUrl(`/auth/external/${encodeURIComponent(provider.key)}/start?returnTo=${encodeURIComponent(redirect)}&rememberMe=${rememberMe}`));
  };
  return (
    <main className='login-page'>
      <section className='login-hero'>
        <div className='hero-content'>
          <h1>LBL SHIT</h1>
          <p>你把核心系统交给劳务派遣来开发</p>
          <p>说明你也没把他当核心</p>
        </div>
        <footer>只要不报错，就是好系统</footer>
      </section>
      <section className='login-panel'>
        <div className='login-form-wrap'>
          {/* login-body 撑满剩余高度并让内容成组垂直居中，版权因此被挤到底部 */}
          <div className='login-body'>
            <LoginBrand />
            <div className='login-heading'>
              <h2>欢迎登录</h2>
            </div>
            <Form form={form} layout='vertical' initialValues={{ rememberMe: true, username: '', password: '' }} onFinish={onFinish} requiredMark={false}>
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
                <span className='login-account-links'>
                  <button type='button' className='login-option-link' onClick={() => message.info('忘记密码后请联系管理员重置')}>
                    忘记密码
                  </button>
                  <i aria-hidden='true' />
                  <Link to='/register'>注册账号</Link>
                </span>
              </div>
              <Button htmlType='submit' size='large' type='primary' block loading={submitting} disabled={submitting}>
                登录
              </Button>
            </Form>
            {providers.length > 0 && (
              <div className='login-social'>
                <div className='login-social-divider'>其他登录方式</div>
                <div className='login-social-buttons'>
                  {providers.map((provider) => (
                    <Tooltip key={provider.key} title={`${provider.displayName}${provider.enabled ? '' : '（未启用）'}`}>
                      <Button
                        size='large'
                        shape='circle'
                        aria-label={`${provider.displayName}登录${provider.enabled ? '' : '，未启用'}`}
                        aria-disabled={!provider.enabled}
                        className={`social-icon-button social-${provider.key}${provider.enabled ? '' : ' is-disabled'}`}
                        icon={providerIcons[provider.icon] ?? <UserOutlined />}
                        onClick={() => onThirdParty(provider)}
                      />
                    </Tooltip>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className='login-copyright'>© 2026 LBL SHIT　版权所有</div>
        </div>
      </section>
    </main>
  );
}
