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
 *
 * 交付不了任何页面时返回 null，**不再回退到某个写死的路径**。
 * 原因：以前回退成 '/home'，而调用方拿到的会是一个"当前账号根本没被授权的地址"——
 * 打开它必然渲染 403 或 404，而那两个页面上的「返回首页」按钮又用同一个函数算出
 * '/home'，于是又 403/404，用户就卡在原地来回跳，永远出不去。返回 null 把
 * "没有落点"这个事实交给调用方处理，才能给出真正的出口。
 */
export function firstAvailablePath(menus: MenuRoute[]): string | null {
  return pageMenus(menus).find((menu) => menu.visible !== 0)?.routePath ?? null;
}

/** 页面内跳转一律传站内路径；本函数只负责把它规整成 pathname。 */
export function navigateTarget(path: string): { pathname: string } {
  return { pathname: path };
}

/**
 * 校验来自 URL 的 redirect 参数，防止开放重定向（钓鱼跳板）。
 *
 * 威胁模型：攻击者发一个 `https://你的系统/login?redirect=https://钓鱼站`。
 * 受害者看到的是自己的域名、自己的登录框、登录成功后还会弹出「登录成功」——
 * 然后被整页跳到钓鱼站。这是最容易被信任的一种钓鱼手法，必须在跳转前拦住。
 *
 * 只接受"站内绝对路径"：以单个 `/` 开头。用 URL 解析而不是正则，是为了让浏览器
 * 自己按 RFC 3986 处理各种畸形写法，避免手写规则漏判：
 *   - `//evil.com`   → 协议相对地址，host 变成 evil.com → 拒绝
 *   - `https://evil` → 绝对地址，origin 不同        → 拒绝
 *   - `/\evil.com`   → 部分浏览器同样按 // 处理     → 由 origin 比对兜住
 *
 * 注意：这里只保证"跳的是我们站点"，**不保证目标页面存在或已授权**。
 * 那属于路由守卫的职责（见 DynamicPage），不要在这里用后端目录做白名单——
 * 登录时该目录可能还没加载，而且目录是"全站配置过的页面"，不等于"这个账号能访问的页面"。
 */
export function resolveRedirect(raw: string | null | undefined, origin: string): string | null {
  if (!raw) return null;
  try {
    const url = new URL(raw, origin);
    if (url.origin !== origin) return null;
    return url.pathname + url.search + url.hash;
  } catch {
    // 连 URL 都解析不出来（例如含非法字符），一律当不可信处理。
    return null;
  }
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
