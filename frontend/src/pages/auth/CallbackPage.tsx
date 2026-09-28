import { useEffect, useRef } from 'react';
import { App } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchMe, refresh } from '../../api/auth';
import { useAppDispatch } from '../../store/hooks';
import { clearSession, setProfile, setSession } from '../../store/authSlice';
import { firstAvailablePath, resolveRedirect } from '../../router/menu';
import { LoadingScreen } from '../../components/common/LoadingScreen';

export default function CallbackPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { message } = App.useApp();
  // React StrictMode 会在开发环境额外执行一次 effect；回调页有提示和跳转，必须只处理一次。
  const handledRef = useRef(false);
  useEffect(() => {
    if (handledRef.current) return;
    handledRef.current = true;
    const params = new URLSearchParams(location.search);
    const error = params.get('error');
    if (error) {
      message.error(error);
      // 外部账号“绑定”失败时后端会把 returnTo 指向账户安全页。Cookie 仍在，AuthGuard
      // 会静默恢复会话；不能一律跳登录页，否则用户会误以为自己被踢下线。
      navigate(resolveRedirect(params.get('returnTo'), window.location.origin) ?? '/login', { replace: true });
      return;
    }
    void (async () => {
      try {
        const { accessToken } = await refresh();
        const profile = await fetchMe();
        dispatch(setSession({ accessToken, principalType: profile.principalType, user: profile.user, onboarding: profile.onboarding }));
        dispatch(setProfile(profile));
        if (profile.principalType === 'ONBOARDING') navigate('/account/setup', { replace: true });
        else navigate(resolveRedirect(params.get('returnTo'), window.location.origin) ?? firstAvailablePath(profile.menus) ?? '/account/security', { replace: true });
      } catch {
        dispatch(clearSession());
        message.error('外部登录状态已失效，请重新登录');
        navigate('/login', { replace: true });
      }
    })();
  }, [dispatch, location.search, message, navigate]);
  return <LoadingScreen />;
}
