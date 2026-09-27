import { useEffect, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchMe, refresh } from '../../api/auth';
import { clearSession, setProfile, setSession } from '../../store/authSlice';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { LoadingScreen } from './LoadingScreen';

export function AuthGuard({ children }: { children: ReactNode }) {
  const [checking, setChecking] = useState(true);
  const token = useAppSelector((state) => state.auth.accessToken);
  const principalType = useAppSelector((state) => state.auth.principalType);
  const dispatch = useAppDispatch();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    const restore = async () => {
      try {
        const refreshed = token ? null : await refresh();
        const accessToken = token ?? refreshed!.accessToken;
        const profile = await fetchMe();
        if (!active) return;
        dispatch(setSession({ accessToken, principalType: profile.principalType, user: profile.user, onboarding: profile.onboarding }));
        // routes 是全站页面路由目录，DynamicPage 用它区分 403 与 404，必须一起写入。
        dispatch(setProfile({ permissions: profile.permissions, menus: profile.menus, routes: profile.routes }));
        if (profile.principalType === 'ONBOARDING' && location.pathname !== '/account/setup') navigate('/account/setup', { replace: true });
      } catch {
        if (!active) return;
        dispatch(clearSession());
        navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`, { replace: true });
      } finally {
        if (active) setChecking(false);
      }
    };
    void restore();
    return () => {
      active = false;
    };
  }, [dispatch, location.pathname, navigate, token]);

  /**
   * 开户确认态（ONBOARDING）只允许停在 /account/setup 一个落点上。
   * <p>
   * effect 里已经会把人送到那里，但"检查完成 → children 可见"这一帧仍会先渲染一次
   * 被保护的页面（通常是 AppLayout 工作台），于是工作台会立刻发起一批对开户态<b>必然 403</b>
   * 的请求：未读消息数、实时连接票据（还会因此建一次注定失败的 WebSocket 重连退避）。
   * 除了控制台噪音，也会让"这个临时身份到底能不能用系统"变得难以判断。
   * 因此在跳转生效前保持加载态 —— 与被保护页面本身无关，纯属"这一帧不该渲染"。
   */
  const onboardingElsewhere = principalType === 'ONBOARDING' && location.pathname !== '/account/setup';
  if (checking || onboardingElsewhere) return <LoadingScreen />;
  return token && principalType ? <>{children}</> : null;
}
