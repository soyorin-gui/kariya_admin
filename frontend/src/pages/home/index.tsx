import { useMemo, useState } from 'react';
import { App, Avatar, Badge, Button, Card, Col, Divider, Input, Progress, Row, Segmented, Space, Switch, Tag, Typography } from 'antd';
import { CheckCircleOutlined, CodeOutlined, CopyOutlined, DeploymentUnitOutlined, LineChartOutlined, PlayCircleOutlined } from '@ant-design/icons';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import type { GraphOptions } from '@antv/g6';
import { BaseChart } from '../../components/BaseChart';
import { CodeViewer } from '../../components/CodeViewer';
import { CopyButton } from '../../components/CopyButton';
import { G6Graph } from '../../components/G6Graph';
import { StatusTag } from '../../components/StatusTag';
import { useThemePreference } from '../../theme/ThemeProvider';
import './home.css';

const DEMO_SNIPPET = `import { BaseChart } from '@/components/BaseChart';

const option = {
  xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed'] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: [18, 32, 26] }],
};

<BaseChart option={option} height={280} />;`;

function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '');
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** 组件展示页不读取业务接口；所有内容均为本地 mock，供后续页面开发参考。 */
export default function HomePage() {
  const { message } = App.useApp();
  const { primaryColor, semantic } = useThemePreference();
  const [dense, setDense] = useState(false);
  const [segment, setSegment] = useState<string | number>('概览');
  const [draft, setDraft] = useState('可在此输入内容，观察 Ant Design Input 的默认状态。');

  const chartOption = useMemo<EChartsOption>(
    () => ({
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 20, top: 34, bottom: 30 },
      xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], axisLine: { lineStyle: { color: '#d7e1ef' } } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(128, 150, 180, .18)' } } },
      series: [
        {
          name: 'Mock requests',
          type: 'line',
          smooth: true,
          data: [24, 36, 28, 52, 46, 68, 59],
          symbolSize: 7,
          lineStyle: { width: 3, color: primaryColor },
          itemStyle: { color: primaryColor },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: withAlpha(primaryColor, 0.3) },
              { offset: 1, color: withAlpha(primaryColor, 0.02) },
            ]),
          },
        },
      ],
    }),
    [primaryColor],
  );

  const graphOptions = useMemo<Omit<GraphOptions, 'container' | 'height' | 'autoResize'>>(
    () => ({
      data: {
        nodes: [
          { id: 'client', style: { x: 80, y: 130, size: 48, fill: withAlpha(primaryColor, 0.14), stroke: primaryColor, lineWidth: 2, labelText: 'Client', labelPlacement: 'bottom' } },
          { id: 'api', style: { x: 245, y: 72, size: 52, fill: primaryColor, stroke: primaryColor, labelText: 'API', labelFill: primaryColor, labelPlacement: 'bottom' } },
          { id: 'cache', style: { x: 245, y: 194, size: 48, fill: withAlpha(semantic.success, 0.16), stroke: semantic.success, lineWidth: 2, labelText: 'Cache', labelPlacement: 'bottom' } },
          { id: 'database', style: { x: 420, y: 130, size: 52, fill: withAlpha(semantic.warning, 0.16), stroke: semantic.warning, lineWidth: 2, labelText: 'Database', labelPlacement: 'bottom' } },
        ],
        edges: [
          { source: 'client', target: 'api', style: { stroke: withAlpha(primaryColor, 0.58), lineWidth: 2, endArrow: true } },
          { source: 'api', target: 'cache', style: { stroke: withAlpha(semantic.success, 0.62), lineWidth: 2, endArrow: true } },
          { source: 'api', target: 'database', style: { stroke: withAlpha(semantic.warning, 0.62), lineWidth: 2, endArrow: true } },
        ],
      },
      node: { type: 'circle' },
      edge: { type: 'line' },
      // G6 v5 已将 v4 的 drag-node 改名为 drag-element。
      behaviors: ['drag-canvas', 'drag-element', 'zoom-canvas'],
    }),
    [primaryColor, semantic.success, semantic.warning],
  );

  return (
    <div className='component-lab-page'>
      <div className='page-head component-lab-head'>
        <div>
          <div className='page-kicker'>COMPONENT LAB</div>
          <Typography.Title level={2} className='page-title'>前端组件示例</Typography.Title>
          <Typography.Paragraph type='secondary' className='component-lab-subtitle'>所有内容均为本地 mock，用作后续页面开发时的样式和调用参考。</Typography.Paragraph>
        </div>
        <Tag color='processing' icon={<CodeOutlined />}>开发参考页</Tag>
      </div>

      <Card className='showcase-card antd-showcase' title='Ant Design 基础交互' extra={<Badge status='success' text='Mock state' />}>
        <Row gutter={[24, 20]}>
          <Col xs={24} lg={13}>
            <div className='demo-label'>按钮、状态与反馈</div>
            <Space wrap size={[10, 10]}>
              <Button type='primary' icon={<PlayCircleOutlined />} onClick={() => message.success('这是一个成功反馈示例')}>主按钮</Button>
              <Button icon={<CopyOutlined />} onClick={() => message.info('这是一个普通反馈示例')}>普通按钮</Button>
              <Button danger>危险操作</Button>
              <StatusTag status='success'>运行正常</StatusTag>
              <StatusTag status='warning'>等待处理</StatusTag>
              <StatusTag status='error'>请求失败</StatusTag>
            </Space>
            <Divider />
            <div className='demo-label'>输入与选择</div>
            <Space direction='vertical' size={10} className='demo-input-stack'>
              <Input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder='输入任意内容' />
              <Space wrap>
                <Segmented value={segment} onChange={setSegment} options={['概览', '详情', '日志']} />
                <Switch checked={dense} checkedChildren='紧凑' unCheckedChildren='舒适' onChange={setDense} />
              </Space>
            </Space>
          </Col>
          <Col xs={24} lg={11}>
            <div className={`mini-status-panel${dense ? ' is-dense' : ''}`}>
              <div className='mini-status-top'>
                <Avatar style={{ backgroundColor: primaryColor }}>UI</Avatar>
                <div><strong>组件状态卡片</strong><span>Segmented：{segment}</span></div>
                <CheckCircleOutlined className='mini-status-icon' style={{ color: semantic.success }} />
              </div>
              <Progress percent={72} strokeColor={primaryColor} trailColor={withAlpha(primaryColor, 0.12)} />
              <Typography.Text type='secondary'>可参考此结构组合 Card、Avatar、Badge、Progress 与 Tag。</Typography.Text>
            </div>
          </Col>
        </Row>
      </Card>

      <Row gutter={[18, 18]} className='showcase-row'>
        <Col xs={24} xl={14}>
          <Card className='showcase-card' title={<Space><LineChartOutlined />ECharts / BaseChart</Space>} extra={<Tag>响应式容器</Tag>}>
            <Typography.Paragraph type='secondary'>`BaseChart` 统一处理初始化、销毁、容器 resize、loading 与 empty 状态。</Typography.Paragraph>
            <BaseChart option={chartOption} height={280} />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card className='showcase-card' title={<Space><DeploymentUnitOutlined />AntV G6</Space>} extra={<Tag>可拖拽 / 缩放</Tag>}>
            <Typography.Paragraph type='secondary'>组件封装了 G6 实例生命周期；图数据、样式和交互仅作为 options 传入。</Typography.Paragraph>
            <G6Graph options={graphOptions} height={280} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[18, 18]} className='showcase-row'>
        <Col xs={24} xl={9}>
          <Card className='showcase-card copy-showcase' title='CopyButton' extra={<CopyButton value='https://api.example.internal/v1/users' successMessage='示例地址已复制' />}>
            <Typography.Paragraph type='secondary'>把复制状态、浏览器权限错误与 Tooltip 统一封装，业务页面只传入文本即可。</Typography.Paragraph>
            <code>https://api.example.internal/v1/users</code>
            <Space className='copy-showcase-action'><CopyButton type='primary' value={'curl -H "Authorization: Bearer <token>" https://api.example.internal/v1/users'} successMessage='curl 示例已复制'>复制请求示例</CopyButton></Space>
          </Card>
        </Col>
        <Col xs={24} xl={15}>
          <Card className='showcase-card' title='CodeViewer' extra={<Tag color='blue'>JSON / XML 支持格式化</Tag>}>
            <Typography.Paragraph type='secondary'>支持查找、字号、复制、下载和全屏；这里使用可编辑模式以便直接试用。</Typography.Paragraph>
            <CodeViewer value={DEMO_SNIPPET} language='typescript' filename='base-chart-demo.tsx' readOnly={false} height={300} />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
