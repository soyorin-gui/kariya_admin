import { useState } from 'react';
import { App, Button, Checkbox, Form, Input } from 'antd';
import { EyeInvisibleOutlined, EyeTwoTone, GithubOutlined, GoogleOutlined, LockOutlined, UserOutlined, WechatOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { login, fetchMe } from '../../api/auth';
import { useAppDispatch } from '../../store/hooks';
import { setProfile, setSession } from '../../store/authSlice';
import { LoginBrand } from './LoginBrand';
import { firstAvailablePath, resolveRedirect } from '../../router/menu';
import { getApiErrorMessage } from '../../utils/apiError';
import './login.css';

/** 第三方登录入口。接入后把 onSelect 换成真正的跳转/换取令牌逻辑即可。 */
const THIRD_PARTY_PROVIDERS = [
  { key: 'wechat', label: '微信', icon: <WechatOutlined />, className: 'social-wechat' },
  { key: 'github', label: 'GitHub', icon: <GithubOutlined />, className: 'social-github' },
  { key: 'google', label: 'Google', icon: <GoogleOutlined />, className: 'social-google' },
];

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
      const profile = await fetchMe();
      dispatch(setSession({ accessToken: result.accessToken, user: profile.user }));
      dispatch(setProfile(profile));
      // 没有显式 redirect 时落到「我的第一项可见页面」：菜单是按角色授权的，
      // 写死 /home 会让没被授予首页的用户一登录就撞 403。
      const redirect = resolveRedirect(new URLSearchParams(location.search).get('redirect'), window.location.origin)
        ?? firstAvailablePath(profile.menus);
      // 一个可见页面都没有时给出明确兜底，避免带着 undefined 去 navigate。
      message.success(response.message || '登录成功');
      navigate(redirect ?? '/404', { replace: true });
    } catch (error) {
      const errorMessage = getApiErrorMessage(error, '登录失败，请检查系统服务');
      form.setFields([{ name: 'password', errors: [errorMessage] }]);
      message.error(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };
  const onThirdParty = (label: string) => {
    // TODO: 接入第三方登录。后端的会话签发（sessions.create + jwt.issue）可以直接复用，
    // 拿到第三方回调的 openid/userinfo 后换成本系统的用户即可。
    message.info(`${label}登录尚未接入`);
  };
  return (
    <main className='login-page'>
      <section className='login-hero'>
        <div className='orb orb-one' />
        <div className='orb orb-two' />
        <div className='hero-content'>
          <div className='hero-rule' />
          <h1>LBL SHIT</h1>
          <p>你把核心系统交给劳务派遣来开发</p>
          <p>说明你也没把他当核心</p>
        </div>
        <footer>
          <span />
          只要不报错，就是好系统
        </footer>
      </section>
      <section className='login-panel'>
        <div className='login-form-wrap'>
          {/* login-body 撑满剩余高度并让内容成组垂直居中，版权因此被挤到底部 */}
          <div className='login-body'>
            <LoginBrand />
            <div className='login-heading'>
              <h2>欢迎登录</h2>
              <p>请输入账号信息以继续访问系统</p>
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
                {/*
                  这里原本放着「忘记密码」和「用户注册」两个 Link，但两条路由都不存在，
                  后端也没有对应接口：点下去会落到通配路由 → DynamicPage → 判定为 404，
                  未登录时还会先被 AuthGuard 弹回登录页，等于一个点了只会绕圈的死链。
                  功能补齐后（需要后端加注册/找回接口）再把入口加回来，别提前挂死链。
                */}
              </div>
              <Button htmlType='submit' size='large' type='primary' block loading={submitting}>
                登录
              </Button>
            </Form>
            <div className='login-social'>
              <div className='login-social-divider'>其他登录方式</div>
              <div className='login-social-buttons'>
                {THIRD_PARTY_PROVIDERS.map((provider) => (
                  <Button key={provider.key} size='large' className={provider.className} icon={provider.icon} onClick={() => onThirdParty(provider.label)}>
                    {provider.label}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <div className='login-copyright'>© 2026 LBL SHIT　版权所有</div>
        </div>
      </section>
    </main>
  );
}
