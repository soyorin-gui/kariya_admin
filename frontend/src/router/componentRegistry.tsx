import { lazy, type ComponentType } from 'react';

/**
 * 构建期页面组件注册表 —— 整套「动态路由」里唯一需要理解的一点。
 *
 * `import.meta.glob` 是 Vite 的构建期能力：`npm run dev` / `npm run build` 时它会扫描磁盘上
 * src/pages 下的所有 .tsx，生成 `{ '../pages/system/user/index.tsx': () => import(...) }` 这样的映射。
 *
 * 关键结论：**新增页面不需要改任何 map**。在 pages 下建出文件、菜单里填上对应的 component 值即可。
 * 唯一的前提是这个文件必须进入本次构建产物：
 *   - 开发模式（vite dev server）：保存文件即生效；
 *   - 生产环境：需要重新 `npm run build` 并发布。
 * 这是编译型 SPA 的固有限制，不是实现上的偷懒 —— 浏览器无法在运行时执行一个从未被编译过的 .tsx。
 * 若确实需要「不重新构建也能上新页面」，那属于微前端 / Module Federation 的范畴（见组件注册说明）。
 *
 * error/ 目录被排除：那是错误页，不是业务页面，不应该出现在菜单的组件候选里。
 */
const modules = import.meta.glob(['../pages/**/*.tsx', '!../pages/error/**']) as Record<string, () => Promise<{ default: ComponentType }>>;

const PREFIX = '../pages/';
const SUFFIX = '.tsx';

/**
 * 解析结果必须缓存。
 * React.lazy() 每次调用都返回一个**全新**组件类型，如果每次渲染都重新 lazy 一次，
 * React 会认为是另一个组件从而卸载重建 —— 表现为页面反复刷新、接口被重复请求。
 */
const resolved = new Map<string, ComponentType>();

function normalize(component: string): string {
  return component.trim().replace(/^\/+/, '').replace(/\.tsx$/i, '');
}

/**
 * 把菜单里的 component 字符串解析成页面组件。
 * 解析不到时返回 null，由调用方渲染 PageUnavailable 诊断页，而不是静默显示 404。
 */
export function resolveMenuComponent(component?: string | null): ComponentType | null {
  if (!component || !component.trim()) return null;
  const key = `${PREFIX}${normalize(component)}${SUFFIX}`;
  const cached = resolved.get(key);
  if (cached) return cached;
  const loader = modules[key];
  if (!loader) return null;
  const page = lazy(loader);
  resolved.set(key, page);
  return page;
}

/**
 * 当前前端产物里真实存在的页面清单。
 * 菜单管理的「前端组件」下拉直接读它，因此不会配出一个渲染不出来的菜单。
 */
export function availableComponents(): string[] {
  return Object.keys(modules)
    .map((key) => key.slice(PREFIX.length, -SUFFIX.length))
    .sort();
}

/** 把 component 值翻译成开发者该创建的文件路径，供「组件不存在」诊断页展示。 */
export function expectedPageFile(component?: string | null): string {
  return `src/pages/${normalize(component ?? '')}${SUFFIX}`;
}
