import { useMemo } from 'react';
import { Card, Col, Row, Tag, Typography } from 'antd';
import { ApartmentOutlined, MenuOutlined, SafetyOutlined, TeamOutlined } from '@ant-design/icons';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { BaseChart } from '../../components/BaseChart';
import { StatusTag } from '../../components/StatusTag';
import { useThemePreference } from '../../theme/ThemeProvider';
import './home.css';

/**
 * HEX → rgba 字符串。
 * <p>
 * 为什么需要它：**ECharts（Canvas）画不出 CSS 变量，也不认 `color-mix()`** ——
 * 它需要一个真实的颜色字符串，而图表渐变必须带透明度，HEX 又没法直接加 alpha。
 * 这正是"在设置里换了主题色、图表却纹丝不动"的根因：之前图表里的颜色全是写死的 HEX。
 */
function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '');
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** 固定的辅助强调色（用来在多系列图表里区分不同项）。刻意不跟主题变，作为"第二色"。 */
const ACCENT_PURPLE = '#8c6cff';

function Chart({ kind }: { kind: 'trend' | 'dept' }) {
  // 主题色与语义色从 ThemeProvider 取，而不是写死 —— 换品牌色 / 切深色时图表会一起变。
  const { primaryColor, semantic } = useThemePreference();
  const option = useMemo<EChartsOption>(
    () =>
      kind === 'trend'
        ? {
            grid: { left: 38, right: 18, top: 25, bottom: 28 },
            tooltip: { trigger: 'axis' },
            xAxis: { type: 'category', boundaryGap: false, data: ['4月', '5月', '6月', '7月', '8月', '9月'], axisLine: { lineStyle: { color: '#dfe8f5' } }, axisLabel: { color: '#8293b3' } },
            yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef3fa' } }, axisLabel: { color: '#8293b3' } },
            series: [
              {
                data: [980, 1038, 1062, 1119, 1184, 1286],
                type: 'line',
                smooth: true,
                symbol: 'circle',
                symbolSize: 7,
                lineStyle: { width: 3, color: primaryColor },
                itemStyle: { color: primaryColor },
                areaStyle: {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: withAlpha(primaryColor, 0.28) },
                    { offset: 1, color: withAlpha(primaryColor, 0.02) },
                  ]),
                },
              },
            ],
          }
        : {
            tooltip: { trigger: 'item' },
            legend: { bottom: 0, textStyle: { color: '#7283a5' } },
            series: [
              {
                type: 'pie',
                radius: ['45%', '68%'],
                avoidLabelOverlap: true,
                label: { show: false },
                data: [
                  { value: 486, name: '技术部', itemStyle: { color: primaryColor } },
                  { value: 314, name: '产品部', itemStyle: { color: semantic.success } },
                  { value: 260, name: '运营部', itemStyle: { color: ACCENT_PURPLE } },
                  { value: 226, name: '其他', itemStyle: { color: withAlpha(primaryColor, 0.35) } },
                ],
              },
            ],
          },
    [kind, primaryColor, semantic.success],
  );
  return <BaseChart option={option} height={285} className='chart' />;
}
export default function HomePage() {
  const { primaryColor, semantic } = useThemePreference();
  // 四个卡片的强调色：第 1 个跟品牌色，第 3/4 个跟语义色。
  // 之前四个都是写死的 HEX，换品牌色时它们不会动。
  const stats = [
    { title: '用户数量', value: '1,286', note: '较上月 +8.6%', icon: <TeamOutlined />, color: primaryColor },
    { title: '角色数量', value: '18', note: '本月新增 2 个', icon: <SafetyOutlined />, color: ACCENT_PURPLE },
    { title: '部门数量', value: '12', note: '组织架构稳定', icon: <ApartmentOutlined />, color: semantic.success },
    { title: '菜单数量', value: '46', note: '可用功能项', icon: <MenuOutlined />, color: semantic.warning },
  ];
  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>OVERVIEW</div>
          <Typography.Title level={2} className='page-title'>
            概览面板
          </Typography.Title>
        </div>
        <div className='today'>2026 年 9 月 21 日　星期一</div>
      </div>
      <Row gutter={[18, 18]}>
        {stats.map((s) => (
          <Col xs={24} sm={12} xl={6} key={s.title}>
            <Card className='stat-card' variant='borderless'>
              <div>
                <span>{s.title}</span>
                <strong>{s.value}</strong>
                <small>{s.note}</small>
              </div>
              <div className='stat-icon' style={{ color: s.color, backgroundColor: `${s.color}14` }}>
                {s.icon}
              </div>
            </Card>
          </Col>
        ))}
      </Row>
      <Row gutter={[18, 18]} className='dash-row'>
        <Col xs={24} xl={15}>
          <Card className='chart-card' title='用户增长趋势' extra={<Tag color='blue'>近 6 个月</Tag>}>
            <Chart kind='trend' />
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card className='chart-card' title='部门用户分布'>
            <Chart kind='dept' />
          </Card>
        </Col>
      </Row>
      <Row gutter={[18, 18]} className='dash-row'>
        <Col xs={24} xl={15}>
          <Card className='recent-card' title='最近登录'>
            <div className='login-list'>
              {['admin', 'zhangsan', 'lisi', 'wangwu'].map((name, i) => (
                <div className='login-row' key={name}>
                  <span className='avatar-dot'>{name[0].toUpperCase()}</span>
                  <div>
                    <strong>{name}</strong>
                    <small>{i === 0 ? '管理员' : '系统用户'}</small>
                  </div>
                  <span>
                    2026-09-21 {10 - i}:2{i}
                  </span>
                  <StatusTag status='success' label='登录成功' />
                </div>
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card className='recent-card' title='系统基础信息'>
            <dl className='system-info'>
              <dt>系统名称</dt>
              <dd>LBL Shit</dd>
              <dt>运行环境</dt>
              {/* 曾经这里写的是 "React 19"，而 package.json 里是 ^18.3.1 —— 界面上写了一句假话。
                  现在如实反映。如果以后升级 React/AntD，记得同时改 package.json 和这一行。 */}
              <dd>Spring Boot 3 · React 18</dd>
              <dt>数据库</dt>
              <dd>MySQL 8.0</dd>
              <dt>缓存服务</dt>
              <dd>Redis</dd>
            </dl>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
