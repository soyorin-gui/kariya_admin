import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
export default function NotFound() {
  const n = useNavigate();
  return (
    <Result
      status='404'
      title='页面不存在'
      subTitle='页面组件不存在或尚未发布。'
      extra={
        <Button type='primary' onClick={() => n('/home')}>
          返回首页
        </Button>
      }
    />
  );
}
