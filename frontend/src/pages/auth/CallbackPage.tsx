import { useEffect } from 'react';
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
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const error = params.get('error');
    if (error) {
      message.error(error);
      navigate('/login', { replace: true });
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
