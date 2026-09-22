import { useEffect, useState, type ReactNode } from 'react';
import { Spin } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchMe, refresh } from '../../api/auth';
import { clearSession, setProfile, setSession } from '../../store/authSlice';
import { useAppDispatch, useAppSelector } from '../../store/hooks';

export function AuthGuard({ children }: { children: ReactNode }) {
  const [checking, setChecking] = useState(true);
  const token = useAppSelector((state) => state.auth.accessToken);
  const dispatch = useAppDispatch();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    const restore = async () => {
      try {
        const accessToken = token ?? (await refresh());
        const profile = await fetchMe();
        if (!active) return;
        dispatch(setSession({ accessToken, user: profile.user }));
        // routes 是全站页面路由目录，DynamicPage 用它区分 403 与 404，必须一起写入。
        dispatch(setProfile({ permissions: profile.permissions, menus: profile.menus, routes: profile.routes }));
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

  if (checking)
    return (
      <div className='app-loading'>
        <Spin size='large' />
        <span>系统加载中...</span>
      </div>
    );
  return token ? <>{children}</> : null;
}
