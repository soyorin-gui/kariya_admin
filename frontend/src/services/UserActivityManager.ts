import { touch } from '../api/auth';
export class UserActivityManager {
  private lastTouch = 0;
  private handler = () => {
    if (Date.now() - this.lastTouch > 5 * 60_000) {
      this.lastTouch = Date.now();
      // A rejected touch means the session expired. The Axios interceptor clears the session
      // on the next protected request; suppressing this background rejection avoids console noise.
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
