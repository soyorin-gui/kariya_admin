import logoImage from '../../assets/logo.png';

/** 登录页独立品牌区：文案只服务于登录场景，不与工作台侧栏耦合。 */
export function LoginBrand() {
  return (
    <div className='login-brand'>
      <img src={logoImage} alt='LBL SHIT' />
      <div>
        <strong>LBL SHIT</strong>
        <small>全栈摸鱼基地</small>
      </div>
    </div>
  );
}
