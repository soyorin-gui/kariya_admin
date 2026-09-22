import type { MenuRoute } from '../types/auth';

/**
 * 「真正的页面」= MENU 类型且配了路由地址的菜单。
 * DIR 只是分组、BUTTON 只是权限点，两者都不对应可访问的路由。
 */
export function pageMenus(menus: MenuRoute[]): MenuRoute[] {
  return menus.filter((menu) => menu.menuType === 'MENU' && !!menu.routePath);
}

/** 按当前地址在菜单树里找页面；找不到说明这个地址不在我的可访问范围内。 */
export function findMenuByPath(menus: MenuRoute[], pathname: string): MenuRoute | undefined {
  return pageMenus(menus).find((menu) => menu.routePath === pathname);
}

/**
 * 登录后 / 访问根路径时应该落到哪一页。
 *
 * 这里不能写死 /home：菜单表是按角色授权的，用户完全可能没被授予「首页」，
 * 写死就会变成"一登录就撞 403"。所以取「我的第一项可见页面」。
 * 菜单已由后端按 parent_id, sort_order, id 排好序，直接取首个即可。
 */
export function firstAvailablePath(menus: MenuRoute[], fallback = '/home'): string {
  return pageMenus(menus).find((menu) => menu.visible !== 0)?.routePath ?? fallback;
}

/**
 * 当前地址所属的所有父级目录 key。
 *
 * 侧边栏的展开状态本来写死成 defaultOpenKeys={['/system']}，那在静态菜单时代够用，
 * 一旦菜单可以后台新增就不成立了：新建的目录默认是收起的，刷新或从外部链接直接访问子页面时，
 * 当前页会被藏在一个收起的目录里，看起来像"没选中"。这里按菜单树现算祖先链。
 */
export function ancestorMenuKeys(menus: MenuRoute[], pathname: string): string[] {
  const byId = new Map(menus.map((menu) => [menu.id, menu]));
  const keys: string[] = [];
  let current = menus.find((menu) => menu.routePath === pathname);
  while (current && current.parentId) {
    const parent = byId.get(current.parentId);
    if (!parent) break;
    keys.unshift(parent.routePath ?? `menu-${parent.id}`);
    current = parent;
  }
  return keys;
}

export function menuBreadcrumb(pathname: string, menus: MenuRoute[]): string[] {  const byId = new Map(menus.map((m) => [m.id, m]));
  const item = menus.find((m) => m.routePath === pathname);
  if (!item) return [];
  const chain: string[] = [];
  let current: MenuRoute | undefined = item;
  while (current) {
    chain.unshift(current.menuName);
    current = byId.get(current.parentId);
  }
  return chain;
}
