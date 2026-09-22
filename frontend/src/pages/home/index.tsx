import { useEffect, useRef } from 'react';
import { Card, Col, Row, Tag, Typography } from 'antd';
import { ApartmentOutlined, MenuOutlined, SafetyOutlined, TeamOutlined } from '@ant-design/icons';
import * as echarts from 'echarts';
import './home.css';
const stats = [
  { title: '用户数量', value: '1,286', note: '较上月 +8.6%', icon: <TeamOutlined />, color: '#1677ff' },
  { title: '角色数量', value: '18', note: '本月新增 2 个', icon: <SafetyOutlined />, color: '#805ad5' },
  { title: '部门数量', value: '12', note: '组织架构稳定', icon: <ApartmentOutlined />, color: '#11a572' },
  { title: '菜单数量', value: '46', note: '可用功能项', icon: <MenuOutlined />, color: '#f29224' },
];
function Chart({ kind }: { kind: 'trend' | 'dept' }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const chart = echarts.init(ref.current!);
    chart.setOption(
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
                lineStyle: { width: 3, color: '#1677ff' },
                areaStyle: {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: 'rgba(22,119,255,.28)' },
                    { offset: 1, color: 'rgba(22,119,255,.02)' },
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
                  { value: 486, name: '技术部', itemStyle: { color: '#1677ff' } },
                  { value: 314, name: '产品部', itemStyle: { color: '#52c41a' } },
                  { value: 260, name: '运营部', itemStyle: { color: '#8c6cff' } },
                  { value: 226, name: '其他', itemStyle: { color: '#b3c8e8' } },
                ],
              },
            ],
          },
    );
    const resize = () => chart.resize();
    window.addEventListener('resize', resize);
    return () => {
      window.removeEventListener('resize', resize);
      chart.dispose();
    };
  }, [kind]);
  return <div ref={ref} className='chart' />;
}
export default function HomePage() {
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
                  <Tag color='success'>登录成功</Tag>
                </div>
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card className='recent-card' title='系统基础信息'>
            <dl className='system-info'>
              <dt>系统名称</dt>
              <dd>Kariya Admin</dd>
              <dt>运行环境</dt>
              <dd>Spring Boot 3 · React 19</dd>
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
