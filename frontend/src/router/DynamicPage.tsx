import { Suspense, useMemo } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Button, Result } from 'antd';
import Forbidden from '../pages/error/Forbidden';
import PageUnavailable from '../pages/error/PageUnavailable';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { clearSession } from '../store/authSlice';
import { logout } from '../api/auth';
import { expectedPageFile, resolveMenuComponent } from './componentRegistry';
import { findMenuByPath, firstAvailablePath, navigateTarget } from './menu';
import { LoadingScreen } from '../components/common/LoadingScreen';

/**
 * 动态页面出口：整个后台的业务页面都由这一个组件渲染。
 *
 * 判定顺序（三级，缺一不可）：
 *   1. 地址在我的菜单树里              → 渲染菜单 component 指向的页面
 *   2. 不在我的菜单树、但全站配置里有   → 页面存在但我没权限 → 403
 *   3. 全站都没配过这个地址            → 404
 *
 * 为什么「在我的菜单树里」就等于「有权限访问」：
 * MenuMapper.selectByUserId 已用递归 CTE 算出"这个用户能访问的菜单集合"
 * （role → role_menu → menu，且菜单 status=1、角色 status=1 且未删除，并补齐祖先节点）。
 * 这张菜单树本身就是授权结果，不需要再在前端维护一份「路径 → 权限码」的映射表。
 *
 * 注意 visible=0（隐藏）的菜单仍然可以访问，只是不出现在侧边栏 —— 这是"隐藏"应有的语义
 * （详情页、带参数的内部页常用这种做法），所以不要在这里过滤 visible。
 */
export function DynamicPage() {
  const { pathname } = useLocation();
  const menus = useAppSelector((s) => s.auth.menus);
  const catalog = useAppSelector((s) => s.auth.routes);
  const menu = useMemo(() => findMenuByPath(menus, pathname), [menus, pathname]);
  const Page = useMemo(() => resolveMenuComponent(menu?.component), [menu?.component]);

  if (menu) {
    if (!Page) {
      return <PageUnavailable menuName={menu.menuName} component={menu.component} file={expectedPageFile(menu.component)} />;
    }
    return (
      <Suspense fallback={<LoadingScreen />}>
        {/* 页面是 lazy 加载的：echarts 这类重依赖会因此被拆成独立 chunk，只在真正访问时下载。 */}
        <Page />
      </Suspense>
    );
  }
  if (catalog.some((item) => item.routePath === pathname)) return <Forbidden />;
  // 404 intentionally leaves the workbench shell. It is a standalone browser page rather
  // than an empty slot between the sidebar and header.
  return <Navigate to='/404' replace />;
}

/**
 * 根路径落点：优先「我的第一项可见页面」。
 * <p>
 * 一个可见页面都没有时（例如账号只被授了按钮权限、还没有任何菜单），以前会回退到写死的
 * '/home'，而那多半是个没配过或没授权的地址——结果就是登录后立刻看到 403/404，
 * 并且因为 403/404 页上的出口按钮用同一个函数，用户会被永久困在错误页里。
 * 这里改为在当前位置给出一个明确的"无可用页面"空状态：
 * 不再跳到任何算出来的地址，循环自然消失，用户也始终有"退出登录"这条路可走。
 * <p>
 * 之所以不直接渲染 <NotFound/>：那是整屏独立页（main + 自身 100vh 布局），塞进
 * AppLayout 的 Content 里会嵌套两层整屏容器；而且它的按钮是"去登录"，对已登录用户没有意义。
 */
export function MenuHomeRedirect() {
  const menus = useAppSelector((s) => s.auth.menus);
  const landing = firstAvailablePath(menus);
  if (landing) return <Navigate to={navigateTarget(landing)} replace />;
  return <NoAccessiblePage />;
}

function NoAccessiblePage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  return (
    <Result
      status='info'
      title='当前账号还没有可访问的页面'
      subTitle='你的角色尚未被授予任何菜单权限，请联系系统管理员完成授权；授权后重新登录即可看到入口。'
      extra={
        <Button
          type='primary'
          onClick={() => {
            void logout();
            dispatch(clearSession());
            // 与顶栏「退出登录」保持一致：清完会话显式回登录页，用 replace 避免把
            // 这个受保护的空状态留在历史栈里被"后退"回来。
            void navigate('/login', { replace: true });
          }}
        >
          退出登录
        </Button>
      }
    />
  );
}
