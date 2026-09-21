import type { MenuRoute } from '../types/auth';
export function menuBreadcrumb(pathname: string, menus: MenuRoute[]): string[] {
  const byId = new Map(menus.map((m) => [m.id, m]));
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
