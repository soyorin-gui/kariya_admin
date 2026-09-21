import { Empty, Typography } from 'antd';
export default function PlaceholderPage({ title }: { title: string }) {
  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <Typography.Title level={2} className='page-title'>
            {title}
          </Typography.Title>
          <p>该模块已接入动态菜单，可在此继续扩展业务配置。</p>
        </div>
      </div>
      <div className='table-card' style={{ padding: '80px 20px' }}>
        <Empty description={`${title}功能即将完善`} />
      </div>
    </div>
  );
}
