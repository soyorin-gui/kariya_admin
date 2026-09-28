import { useEffect, useRef } from 'react';
import { Graph, type GraphOptions } from '@antv/g6';
import './g6Graph.css';

export interface G6GraphProps {
  /** 组件管理容器、高度及自动 resize，业务页面只需提供图数据和交互配置。 */
  options: Omit<GraphOptions, 'container' | 'height' | 'autoResize'>;
  height?: number;
  className?: string;
}

/** AntV G6 的 React 生命周期封装：创建、销毁与容器尺寸变化都在此处收口。 */
export function G6Graph({ options, height = 300, className }: G6GraphProps) {
  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    // G6 的 render 是异步的。React StrictMode / 热更新会在首次 render 尚未完成时执行 cleanup，
    // 直接 destroy 会让仍在执行的绘制任务访问已销毁实例，出现 "graph instance has been destroyed"。
    // 每个实例有独立挂载节点，待 render 收尾后再销毁，避免前后实例互相清空画布。
    const mount = document.createElement('div');
    mount.style.width = '100%';
    mount.style.height = '100%';
    host.append(mount);
    const graph = new Graph({ ...options, container: mount, height, autoResize: true });
    let cleanupRequested = false;
    void graph.render()
      .catch((error: unknown) => {
        if (!cleanupRequested) console.error('G6 图渲染失败', error);
      })
      .finally(() => {
        if (cleanupRequested && !graph.destroyed) graph.destroy();
        if (cleanupRequested) mount.remove();
      });
    return () => {
      cleanupRequested = true;
      if (graph.rendered && !graph.destroyed) graph.destroy();
      if (graph.rendered) mount.remove();
    };
  }, [height, options]);

  return <div ref={hostRef} className={`g6-graph${className ? ` ${className}` : ''}`} style={{ height }} />;
}
