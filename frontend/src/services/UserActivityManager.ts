import { touch } from '../api/auth';
export class UserActivityManager {
  private lastTouch = 0;
  private handler = () => {
    if (Date.now() - this.lastTouch > 5 * 60_000) {
      this.lastTouch = Date.now();
      // touch 请求被拒表示会话已过期。下一次受保护请求会由 Axios 拦截器清理会话；
      // 此处吞掉后台请求异常，避免控制台产生无意义噪声。
      void touch().catch(() => undefined);
    }
  };
  start() {
    ['click', 'keydown', 'input', 'pointerdown', 'scroll'].forEach((e) => window.addEventListener(e, this.handler, { passive: true }));
  }
  stop() {
    ['click', 'keydown', 'input', 'pointerdown', 'scroll'].forEach((e) => window.removeEventListener(e, this.handler));
  }
}
