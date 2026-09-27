import { useEffect, useState } from 'react';
import { App, Button, Checkbox, Form, Input } from 'antd';
import { LockOutlined, MailOutlined, PhoneOutlined, SafetyOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { fetchMe, getCaptcha, register } from '../../api/auth';
import { LoginBrand } from '../login/LoginBrand';
import { useAppDispatch } from '../../store/hooks';
import { setProfile, setSession } from '../../store/authSlice';
import { firstAvailablePath } from '../../router/menu';
import { getApiErrorMessage } from '../../utils/apiError';
import { PASSWORD_MESSAGE, PASSWORD_PATTERN } from '../../utils/passwordPolicy';
import type { CaptchaChallenge, RegistrationRequest } from '../../types/auth';
import '../login/login.css';
import './register.css';

type FormValues = RegistrationRequest;

/**
 * 图形验证码输入行。
 * <p>
 * 之所以要单独抽一个受控组件，而不是直接在 Form.Item 里放 div + Input：
 * Form.Item 只会把 value/onChange 注入给**直接子元素**，而直接子元素必须是个表单控件。
 * 放一个 div 的话绑定会失效（表现为"输入了但表单拿不到值"）。
 */
function CaptchaField({ value, onChange, challenge, loading, onRefresh }: {
  value?: string;
  onChange?: (value: string) => void;
  challenge: CaptchaChallenge | null;
  loading: boolean;
  onRefresh: () => void;
}) {
  return (
    <div className='captcha-row'>
      <Input
        size='large'
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
        prefix={<SafetyOutlined />}
        placeholder='图形验证码'
        maxLength={8}
        autoComplete='off'
      />
      <button
        type='button'
        className='captcha-image'
        onClick={onRefresh}
        disabled={loading}
        aria-label='图形验证码，点击换一张'
        title='看不清？点击换一张'
      >
        {loading || !challenge?.image ? <span>加载中…</span> : <img src={challenge.image} alt='图形验证码' />}
      </button>
    </div>
  );
}

export default function RegisterPage() {
  const [submitting, setSubmitting] = useState(false);
  const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  /**
   * 只有后端明确说"关闭了"才隐藏验证码；拿不到题目（加载中 / 请求失败）时一律按
   * "需要验证码"处理。这是刻意的失败方向：验证码接口出问题时，
   * 不应该顺势变成一个不需要验证码的注册入口。
   */
  const captchaRequired = challenge?.enabled !== false;

  const refreshCaptcha = async () => {
    try {
      setCaptchaLoading(true);
      const next = await getCaptcha();
      setChallenge(next);
      form.setFieldValue('captchaCode', '');
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载图形验证码，请稍后重试'));
    } finally {
      setCaptchaLoading(false);
    }
  };

  useEffect(() => {
    void refreshCaptcha();
    // 只在首次挂载时取一张；后续刷新都由用户点击或提交失败触发。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = async (values: FormValues) => {
    try {
      setSubmitting(true);
      const result = await register({ ...values, captchaId: challenge?.captchaId ?? undefined });
      const profile = await fetchMe();
      dispatch(setSession({ accessToken: result.data.accessToken, principalType: profile.principalType, user: profile.user }));
      dispatch(setProfile(profile));
      message.success('注册成功');
      navigate(firstAvailablePath(profile.menus) ?? '/account/security', { replace: true });
    } catch (error) {
      message.error(getApiErrorMessage(error, '注册失败，请稍后重试'));
      // 验证码是"一次性"的：服务端在开始处理时就已经把它消费掉了，
      // 所以不管这次失败是验证码错、用户名重复还是别的业务校验，都必须换一张，
      // 否则用户会拿着一个已经失效的验证码反复提交、永远失败。
      await refreshCaptcha();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className='login-page register-page'>
      <section className='login-hero'>
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
          <div className='login-body'>
            <LoginBrand />
            <div className='login-heading'>
              <h2>注册账号</h2>
            </div>
            <Form form={form} onFinish={submit} initialValues={{ rememberMe: true }} requiredMark={false}>
              <div className='register-grid'>
                <Form.Item
                  name='username'
                  rules={[
                    { required: true, message: '请输入用户名' },
                    { pattern: /^[A-Za-z0-9_.-]+$/, message: '只能包含字母、数字、下划线、点和短横线' },
                  ]}
                >
                  <Input size='large' prefix={<UserOutlined />} placeholder='用户名' autoComplete='username' />
                </Form.Item>
                <Form.Item name='realName' rules={[{ required: true, message: '请输入姓名' }]}>
                  <Input size='large' prefix={<UserOutlined />} placeholder='姓名' />
                </Form.Item>
                <Form.Item name='phone'>
                  <Input size='large' prefix={<PhoneOutlined />} placeholder='手机号（可选）' />
                </Form.Item>
                <Form.Item name='email' rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
                  <Input size='large' prefix={<MailOutlined />} placeholder='联系邮箱（可选）' />
                </Form.Item>
                <Form.Item name='password' rules={[{ required: true, message: '请输入密码' }, { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE }]}>
                  <Input.Password size='large' prefix={<LockOutlined />} placeholder='密码' autoComplete='new-password' />
                </Form.Item>
                <Form.Item
                  name='confirmPassword'
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
                  <Input.Password size='large' prefix={<LockOutlined />} placeholder='确认密码' autoComplete='new-password' />
                </Form.Item>
                {captchaRequired && (
                  <Form.Item name='captchaCode' rules={[{ required: true, message: '请输入图形验证码' }]}>
                    <CaptchaField challenge={challenge} loading={captchaLoading} onRefresh={() => void refreshCaptcha()} />
                  </Form.Item>
                )}
              </div>
              <div className='login-options'>
                <Form.Item name='rememberMe' valuePropName='checked' noStyle>
                  <Checkbox>保持登录</Checkbox>
                </Form.Item>
                <Link to='/login'>返回登录</Link>
              </div>
              <Button htmlType='submit' size='large' type='primary' block loading={submitting}>
                创建账号
              </Button>
            </Form>
          </div>
          <div className='login-copyright'>© 2026 LBL SHIT　版权所有</div>
        </div>
      </section>
    </main>
  );
}
