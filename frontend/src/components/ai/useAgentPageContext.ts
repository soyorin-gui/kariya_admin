import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { findMenuByPath, menuBreadcrumb } from '../../router/menu';
import { useAppSelector } from '../../store/hooks';
import type { AgentPageContext } from '../../types/agent';

/**
 * 从已经过后端授权的菜单树中构造页面上下文。
 *
 * 页面上下文会跟随每一轮请求发送，而不是绑定在整个会话上。因此用户在不关闭助手的情况下
 * 切换页面，下一轮问题会自然使用新页面；旧消息仍可保留。权限仍由后端业务接口判断。
 */
export function useAgentPageContext(): AgentPageContext {
  const { pathname } = useLocation();
  const menus = useAppSelector((state) => state.auth.menus);

  return useMemo(() => {
    const page = findMenuByPath(menus, pathname);
    return {
      routePath: pathname,
      menuId: page?.id,
      pageTitle: page?.menuName,
      breadcrumb: menuBreadcrumb(pathname, menus),
    };
  }, [menus, pathname]);
}
