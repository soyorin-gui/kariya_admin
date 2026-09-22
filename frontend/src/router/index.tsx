import { createBrowserRouter } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import LoginPage from '../pages/login';
import Forbidden from '../pages/error/Forbidden';
import { AuthGuard } from '../components/common/AuthGuard';
import { DynamicPage, MenuHomeRedirect } from './DynamicPage';

/**
 * 静态骨架 + 一个动态出口。
 *
 * 这里只登记「不随业务变化」的东西：登录页、受保护的布局、根路径重定向、403 预览、兜底。
 * **所有业务页面都不在这里登记** —— 它们由后端菜单数据驱动，交给 <DynamicPage/> 解析：
 *     菜单 routePath  →  location.pathname
 *     菜单 component  →  src/pages 下真实存在的文件（见 componentRegistry.tsx）
 *
 * 所以新增一个业务页面的完整流程是：
 *   1. 前端建文件：src/pages/<模块>/index.tsx（默认导出 React 组件）；
 *   2. 浏览器里在「菜单管理」新增一条 MENU 类型菜单，填 routePath 与 component；
 *   3. 给角色授权该菜单；
 *   4. 换用目标账号重新登录（或刷新页面）。
 * 全程**不需要改本文件，也不需要改任何 map**。
 *
 * 关于兜底：'/' 的 '*' 子路由已经能匹配任意路径（'/' 匹配根，'*' 吃掉剩下的段），
 * 因此不存在"未匹配"的情况，也就不需要再放一个顶层 '*'——两个 splat 互相竞争
 * 反而会让 404 落到哪一条变得不确定。未登录用户访问未知路径会先被 AuthGuard 送去登录页，
 * 这比向匿名用户暴露"这个路径存不存在"更合适。
 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <AuthGuard>
        <AppLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <MenuHomeRedirect /> },
      // 403 页面不是业务菜单，保留显式路由便于单独预览自己设计的效果（静态段优先于 * 匹配）。
      { path: '403', element: <Forbidden /> },
      { path: '*', element: <DynamicPage /> },
    ],
  },
]);
