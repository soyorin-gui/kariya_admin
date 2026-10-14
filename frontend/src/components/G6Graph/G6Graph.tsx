import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { Graph, type GraphOptions } from '@antv/g6';
import './g6Graph.css';

export interface G6GraphHandle {
  fitView: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
  resetView: () => void;
}

export interface G6GraphProps {
  /** 组件管理容器、高度及自动 resize，业务页面只需提供图数据和交互配置。 */
  options: Omit<GraphOptions, 'container' | 'height' | 'autoResize'>;
  height?: number;
  className?: string;
  /** 首次完成绘制后将全部节点适配到当前可视区域。 */
  autoFit?: boolean;
}

/** AntV G6 的 React 生命周期封装，并按需提供通用视图控制能力。 */
export const G6Graph = forwardRef<G6GraphHandle, G6GraphProps>(function G6Graph({ options, height = 300, className, autoFit = false }, ref) {
  const hostRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);

  const fitView = () => {
    if (graphRef.current && !graphRef.current.destroyed) {
      void graphRef.current.fitView({ padding: [48, 64, 48, 64] });
    }
  };

  useImperativeHandle(
    ref,
    () => ({
      fitView,
      zoomIn: () => {
        if (graphRef.current && !graphRef.current.destroyed) void graphRef.current.zoomBy(1.2);
      },
      zoomOut: () => {
        if (graphRef.current && !graphRef.current.destroyed) void graphRef.current.zoomBy(0.8);
      },
      resetView: fitView,
    }),
    [],
  );

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    // G6 的 render 是异步的。React StrictMode / 热更新会在首次 render 尚未完成时执行 cleanup，
    // 每个实例使用独立挂载节点，待 render 收尾后再销毁，避免前后实例互相清空画布。
    const mount = document.createElement('div');
    mount.style.width = '100%';
    mount.style.height = '100%';
    host.append(mount);

    const graph = new Graph({ ...options, container: mount, height, autoResize: true });
    graphRef.current = graph;
    let cleanupRequested = false;
    void graph
      .render()
      .then(() => {
        if (autoFit && !cleanupRequested) fitView();
      })
      .catch((error: unknown) => {
        if (!cleanupRequested) console.error('G6 图渲染失败', error);
      })
      .finally(() => {
        if (cleanupRequested && !graph.destroyed) graph.destroy();
        if (cleanupRequested) mount.remove();
      });

    return () => {
      cleanupRequested = true;
      if (graphRef.current === graph) graphRef.current = null;
      if (graph.rendered && !graph.destroyed) graph.destroy();
      if (graph.rendered) mount.remove();
    };
  }, [autoFit, height, options]);
  return <div ref={hostRef} className={`g6-graph${className ? ` ${className}` : ''}`} style={{ height }} />;
});
