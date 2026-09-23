import { Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { firstAvailablePath, navigateTarget } from '../../router/menu';
import { useAppSelector } from '../../store/hooks';
import './notFound.css';

export default function NotFound() {
  const n = useNavigate();
  const menus = useAppSelector((state) => state.auth.menus);
  // 未登录直接访问 /404 时菜单是空的，这里算不出落点；此时不渲染"返回首页"，
  // 否则按钮会指向一个不存在或未授权的地址，点下去再次 404 —— 死循环的来源之一。
  const landing = firstAvailablePath(menus);
  return (
    <main className='not-found-page'>
      <div className='not-found-inner'>
        <div className='not-found-illustration' aria-hidden='true'>
          <img src='/astronaut-404.png' alt='' />
        </div>
        <section className='not-found-copy' aria-labelledby='not-found-title'>
          <p className='not-found-code'>404</p>
          <h1 id='not-found-title'>糟糕，页面飘进银河了</h1>
          <p>
            宇航员找了一圈，也没发现你要找的页面。<br />
            它可能改名了、下线了，或者正在宇宙里摸鱼。
          </p>
          {landing ? (
            <Button type='primary' size='large' onClick={() => n(navigateTarget(landing), { replace: true })}>
              返回首页
            </Button>
          ) : (
            <Button type='primary' size='large' onClick={() => n('/login', { replace: true })}>
              去登录
            </Button>
          )}
        </section>
      </div>
    </main>
  );
}
