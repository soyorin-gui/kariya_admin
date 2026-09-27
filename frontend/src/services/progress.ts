import NProgress from 'nprogress';

/**
 * 全局顶部进度条（nprogress）。
 *
 * <h2>为什么需要额外包一层</h2>
 * 直接调 {@code NProgress.start()/done()} 有三个坑，缺一个它就会从"进度反馈"退化成"闪烁的噪声"：
 *
 * <ol>
 *   <li><b>延迟显示（{@link #DELAY_MS}）</b>：本系统的接口大多在几十毫秒内返回（内网 + 本地）。
 *       不延迟的话，每次点"查询"都会闪一下进度条，比没有更糟。超过 300ms 才认为
 *       "用户开始感知到等待" —— 这是所有成熟实现的通行阈值。</li>
 *   <li><b>并发计数</b>：页面首屏会同时发多个请求（{@code AuthGuard} 的 refresh + me、
 *       {@code AppLayout} 的未读数 + 实时票据）。如果每个请求直接 start/done，
 *       第一个返回的请求就会把还在飞的请求"结束"掉，进度条提前收尾。</li>
 *   <li><b>取消未显示的计时器</b>：300ms 内就结束的请求不应该留下任何痕迹 ——
 *       如果只 start 不取消，计时器到点仍会把进度条画出来，然后立刻被 done 掉，正是那个闪烁。</li>
 * </ol>
 *
 * <h2>两个接入点</h2>
 * <ul>
 *   <li>{@code utils/request.ts} 的 axios 拦截器 —— 覆盖所有 API 请求；</li>
 *   <li>{@code router/componentRegistry.tsx} 的懒加载 loader —— 覆盖"点菜单后下载页面代码"的等待。
 *       这一处尤其有价值：页面是 {@code React.lazy} 懒加载的，首次进入会有几百毫秒空白。</li>
 * </ul>
 *
 * <h2>样式</h2>
 * 基础样式来自 {@code nprogress/nprogress.css}（在 {@code main.tsx} 引入），
 * 品牌色适配在 {@code styles/global.css}（nprogress 默认是硬编码的 {@code #29d}）。
 */

/** 请求快于此值时完全不显示进度条，避免快速接口造成闪烁。 */
const DELAY_MS = 300;

NProgress.configure({
  // 项目里已经有 LoadingScreen（全屏）和 SmartTable / 按钮的 loading 态，
  // 再加一个右上角旋转图标纯属噪声。
  showSpinner: false,
  // 自动递增的间隔（毫秒）。默认 200，调小一点让等待感更"活"。
  trickleSpeed: 180,
  // 起始值。默认 0.08 是一条几乎看不见的线，0.12 更容易被注意到。
  minimum: 0.12,
  easing: 'ease',
  speed: 320,
});

/** 正在进行的被跟踪任务数。归零才真正收尾。 */
let active = 0;
/** 延迟显示用的计时器。非 null 表示"已经开始计数，但还没画出来"。 */
let showTimer: number | null = null;

/** 开始一个被跟踪的任务。通常是成对调用的第一步。 */
export function startProgress(): void {
  active += 1;
  if (active === 1 && showTimer === null) {
    showTimer = window.setTimeout(() => {
      showTimer = null;
      NProgress.start();
    }, DELAY_MS);
  }
}

/** 结束一个被跟踪的任务。计数归零才收尾。 */
export function doneProgress(): void {
  active = Math.max(0, active - 1);
  if (active > 0) return;
  if (showTimer !== null) {
    // 还在延迟窗口内就结束了：取消计时器，不留任何痕迹。
    window.clearTimeout(showTimer);
    showTimer = null;
  }
  // 没被 start 过时 done() 是空操作（nprogress 内部会检查 status），所以这里可以直接调用。
  NProgress.done();
}

/**
 * 强制收尾并清空计数。
 * <p>
 * 用于"会话彻底失效、马上要整页跳转到登录页"这类场景 —— 此时回滚已经在飞的任务计数
 * 已经不现实，必须显式清干净，否则下一次进入应用时进度条可能卡在半路。
 */
export function resetProgress(): void {
  active = 0;
  if (showTimer !== null) {
    window.clearTimeout(showTimer);
    showTimer = null;
  }
  NProgress.done(true);
}

/**
 * 包一个异步任务，自动 start / done（无论成功失败）。
 * <p>
 * 主要给懒加载用：这样进度条反映的是"真的在下载这个页面的代码"，
 * 而不是一个与真实耗时无关的假动画。
 */
export function trackProgress<T>(task: () => Promise<T>): Promise<T> {
  startProgress();
  return task().finally(doneProgress);
}
