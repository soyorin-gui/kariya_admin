import { Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { firstAvailablePath, navigateTarget } from '../../router/menu';
import { useAppSelector } from '../../store/hooks';
import './forbidden.css';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * 403 无权限页面（设计占位，待你自行设计）
 *
 * 触发时机：用户登录后直接输入/收藏了一个自己没有权限的已配置路由地址。
 * 校验位置：src/router/DynamicPage.tsx —— 当前用户菜单中不存在该路径、但 /auth/me
 *          返回的全站路由目录中存在该路径时，渲染本组件。
 *
 * 说明：这一层只是「体验层」，真正的权限边界始终在后端 @PreAuthorize，
 *      前端隐藏入口/拦截路由都无法替代服务端校验，不要把它当安全机制用。
 *
 * 设计约定（改版时请保留）：
 *   1. 必须给用户一个离开本页的出口（至少一个返回首页/上一页的入口），否则用户会卡在这里；
 *      这里的出口指向「我的第一项可见页面」而不是写死的 /home —— 菜单按角色授权，
 *      没被授予首页的用户点了会被再弹回 403，等于没有出口；
 *   2. 文案里不要暴露「需要哪个权限标识」，避免把内部权限模型透给普通用户；
 *   3. 样式请直接重写 ./forbidden.css，不要复用业务表格相关类名。
 *
 * 预览地址：登录后访问 /403
 * ─────────────────────────────────────────────────────────────────────────────
 */
export default function Forbidden() {
  const navigate = useNavigate();
  const menus = useAppSelector((state) => state.auth.menus);
  const landing = firstAvailablePath(menus);
  return (
    <section className='forbidden-page'>
      {/* ↓ ↓ ↓ ↓ ↓ ↓  设计替换区：以下内容整块可重写  ↓ ↓ ↓ ↓ ↓ ↓ */}
      <div className='forbidden-placeholder'>
        <p className='forbidden-code'>403</p>
        <h2 className='forbidden-title'>没有访问该页面的权限</h2>
        <p className='forbidden-desc'>当前账号未被授予此页面的访问权限，请联系系统管理员为你的角色补充授权。</p>
        {/*
          出口只能指向"确实存在且当前账号确实能打开"的页面。算不出落点时（例如账号只被授了
          按钮权限、没有任何菜单），宁可不显示按钮，也不要给一个点了还是回到 403 的假出口 ——
          那会让用户以为页面卡死。design 约定第 1 条要求的正是这一点。
        */}
        {landing && (
          <Button type='primary' onClick={() => navigate(navigateTarget(landing), { replace: true })}>
            返回首页
          </Button>
        )}
      </div>
      {/* ↑ ↑ ↑ ↑ ↑ ↑  设计替换区结束  ↑ ↑ ↑ ↑ ↑ ↑ */}
    </section>
  );
}
