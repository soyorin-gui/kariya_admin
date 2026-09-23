import './loadingScreen.css';

/**
 * 应用启动、鉴权恢复和懒加载页面共用的全屏加载画面。
 *
 * index.html 在 React 接管前使用完全相同的 DOM 与样式，避免刷新时出现两套加载 UI。
 */
export function LoadingScreen() {
  return (
    <div className='app-loading' role='status' aria-label='应用加载中'>
      <div className='app-loading-mark'>LBL</div>
      <div className='app-loading-dots' aria-hidden='true'>
        <i />
        <i />
        <i />
        <i />
        <i />
        <i />
        <i />
        <i />
      </div>
      <p className='app-loading-text'>少女祈祷中...</p>
    </div>
  );
}
