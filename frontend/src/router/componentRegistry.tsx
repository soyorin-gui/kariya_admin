import { lazy, type ComponentType } from 'react';
const modules = import.meta.glob('../pages/**/*.tsx');
export function resolveComponent(component: string): ComponentType {
  const key = `../pages/${component}.tsx`;
  const loader = modules[key];
  if (!loader) {
    console.error(`Component missing: ${component}`);
    return lazy(() => import('../pages/error/NotFound'));
  }
  return lazy(loader as () => Promise<{ default: ComponentType }>);
}
