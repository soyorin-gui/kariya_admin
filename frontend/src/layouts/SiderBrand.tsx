import logoImage from '../assets/logo.png';

/** 工作台侧栏独立品牌区：可与登录页分别维护图标旁的文案。 */
export function SiderBrand() {
  return (
    <div className='sider-brand'>
      <img src={logoImage} alt='LBL SHIT' />
      <div>
        <strong>LBL SHIT</strong>
        <small>全栈摸鱼基地</small>
      </div>
    </div>
  );
}
