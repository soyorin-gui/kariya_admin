import { useEffect, useRef, type CSSProperties } from 'react';
import { Empty, Spin } from 'antd';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { useResizeObserver } from '../../hooks/useResizeObserver';
import './baseChart.css';

export interface BaseChartProps {
  option: EChartsOption;
  loading?: boolean;
  empty?: boolean;
  emptyText?: string;
  height?: number | string;
  className?: string;
  style?: CSSProperties;
  renderer?: 'canvas' | 'svg';
}

export function BaseChart({ option, loading = false, empty = false, emptyText = '暂无数据', height = 360, className, style, renderer = 'canvas' }: BaseChartProps) {
  const chartRef = useRef<echarts.ECharts>();
  const containerRef = useResizeObserver<HTMLDivElement>(() => chartRef.current?.resize());

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const chart = echarts.init(element, undefined, { renderer });
    chartRef.current = chart;
    return () => {
      chart.dispose();
      chartRef.current = undefined;
    };
  }, [containerRef, renderer]);

  useEffect(() => {
    if (!empty) chartRef.current?.setOption(option, { notMerge: true });
  }, [empty, option]);

  useEffect(() => {
    if (loading) chartRef.current?.showLoading('default', { text: '' });
    else chartRef.current?.hideLoading();
  }, [loading]);

  const resolvedHeight = typeof height === 'number' ? `${height}px` : height;
  return (
    <div className={`base-chart${className ? ` ${className}` : ''}`} style={{ ...style, height: resolvedHeight }}>
      <div ref={containerRef} className='base-chart-canvas' />
      {empty && (
        <div className='base-chart-state'>
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
        </div>
      )}
      {loading && empty && (
        <div className='base-chart-state'>
          <Spin />
        </div>
      )}
    </div>
  );
}
