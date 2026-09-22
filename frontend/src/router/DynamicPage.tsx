import { Suspense, useMemo } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import Forbidden from '../pages/error/Forbidden';
import NotFound from '../pages/error/NotFound';
import PageUnavailable from '../pages/error/PageUnavailable';
import { useAppSelector } from '../store/hooks';
import { expectedPageFile, resolveMenuComponent } from './componentRegistry';
import { findMenuByPath, firstAvailablePath } from './menu';

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
      <Suspense
        fallback={
          <div className='app-loading'>
            <Spin size='large' />
            <span>页面加载中...</span>
          </div>
        }
      >
        {/* 页面是 lazy 加载的：echarts 这类重依赖会因此被拆成独立 chunk，只在真正访问时下载。 */}
        <Page />
      </Suspense>
    );
  }
  if (catalog.some((item) => item.routePath === pathname)) return <Forbidden />;
  return <NotFound />;
}

/** 根路径重定向：落到「我的第一项可见页面」，而不是写死的 /home。 */
export function MenuHomeRedirect() {
  const menus = useAppSelector((s) => s.auth.menus);
  return <Navigate to={firstAvailablePath(menus)} replace />;
}
