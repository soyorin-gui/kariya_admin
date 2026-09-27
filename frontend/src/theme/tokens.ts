/**
 * ============================================================================
 *  设计令牌（Design Tokens）—— 全项目默认外观的**唯一定义处**
 * ============================================================================
 *
 * 改这个系统默认长什么样，只需要动这个文件。
 *
 * <h2>⚠️ 为什么必须强调"唯一"：这里曾经有三个陷阱</h2>
 * 之前有三处代码都"看起来"能改主题，但其中两处会被**运行时静默覆盖**，
 * 改了完全没反应，而且不会有任何报错 —— 这是最容易让人以为"主题功能是坏的"的原因：
 *
 * | 你改了哪 | 为什么没生效 |
 * | --- | --- |
 * | `styles/theme.css` 的 `--brand` | `ThemeProvider` 在运行时用 `root.style.setProperty('--brand', …)` 覆盖它。<br>**inline style 优先级高于任何样式表**，所以样式表里那个值只在 React 挂载前生效。 |
 * | 本文件曾经的 `colorPrimary` | `ThemeProvider` 里写的是 `{ ...themeTokens, colorPrimary: preference.primaryColor }` ——<br>后面的键赢，所以 `colorPrimary` **永远**等于用户偏好，本文件里的值被丢弃。 |
 * | `SystemSettings` 界面上改 | ✅ 这一处是好的，唯一真正生效的入口。 |
 *
 * <h2>现在的所有权划分（改代码前先读这张表）</h2>
 * | 令牌 | 谁拥有 | 说明 |
 * | --- | --- | --- |
 * | 品牌色 / 字体 / 字号 / 圆角 | **本文件**（默认值）→ 用户偏好可覆盖 | 会被投射成 AntD token **和** CSS 变量两套 |
 * | 语义色（成功/警告/错误） | **本文件**（含亮暗两套） | 同上 |
 * | 中性色（文字/边框/背景/阴影） | `styles/theme.css` | 纯 CSS，靠 `[data-theme='dark']` 切换，JS 不参与 |
 *
 * <h2>新增一个可切换的外观项要改哪 4 处</h2>
 * 1. 本文件：加选项列表 + 默认值；
 * 2. `ThemeProvider.tsx`：加进 `ThemePreference`、`loadPreference` 校验、以及投射逻辑
 *    （**AntD token 与 CSS 变量两套都要投**，漏一套就会出现"AntD 变了、自定义 CSS 没变"）；
 * 3. `styles/theme.css`：加对应的 CSS 变量（带一个合理的兜底值）；
 * 4. `components/SystemSettings/SystemSettings.tsx`：加控件。
 */

/** 品牌主色预设。`--brand-deep`（hover / 渐变 / 阴影用的深色变体）由 CSS 的 color-mix 自动派生，不需要手写。 */
export const BRAND_COLORS = [
  { name: '科技蓝', value: '#1677ff' },
  { name: '极光紫', value: '#7656d6' },
  { name: '翡翠绿', value: '#18a66a' },
  { name: '珊瑚橙', value: '#e76f35' },
  { name: '海湾青', value: '#1596a8' },
] as const;

/**
 * 字体预设。
 * <p>
 * `stack` 会同时进入 AntD 的 `fontFamily` token 和 `--font-sans` CSS 变量，
 * 因此**同时**影响 AntD 组件（按钮、表格、输入框…）和自定义 CSS。
 * 每一项都给了跨平台兜底：中文优先 PingFang（macOS）/ 微软雅黑（Windows），
 * 再回落到系统无衬线，避免在缺字体的机器上退化成宋体。
 */
export const FONT_FAMILIES = [
  {
    key: 'system',
    name: '系统默认',
    stack: 'Inter, "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  },
  {
    key: 'sans',
    name: '无衬线',
    stack: '"Helvetica Neue", Helvetica, Arial, "PingFang SC", "Microsoft YaHei", sans-serif',
  },
  {
    key: 'serif',
    name: '衬线',
    stack: 'Georgia, "Times New Roman", "Songti SC", SimSun, serif',
  },
  {
    key: 'mono',
    name: '等宽',
    stack: '"JetBrains Mono", "Cascadia Mono", Consolas, "Courier New", "PingFang SC", monospace',
  },
] as const;

export type FontFamilyKey = (typeof FONT_FAMILIES)[number]['key'];

/**
 * 基础字号（px）可选值。
 * <p>
 * 这是**唯一的**字号输入：AntD 的 `fontSize` token 由它派生（`fontSizeSM` / `fontSizeLG` 等
 * 由 AntD 算法自动算出），自定义 CSS 的整套 `--fs-*` 阶梯也由它派生
 * （见 `styles/theme.css`，用 `calc()` 从 `--fs-base` 算出来）。
 * <p>
 * 上下限刻意收在 12–16：再小在 1080p 上不可读，再大表格列会挤成一团
 * （本系统的表格有 8–10 列，且不少列设了固定宽度）。
 */
export const FONT_SIZES = [12, 13, 14, 15, 16] as const;

/** 圆角预设（px）。0 是"直角派"，12 是"圆润派"。 */
export const BORDER_RADII = [0, 4, 6, 8, 12] as const;

/**
 * 语义色。亮暗两套都写在这里，是因为 AntD 的 `darkAlgorithm` 会给出**不同**的成功/警告/错误色
 * （默认亮色 `#18a66a` 在深色背景上偏闷）。如果只写一套然后两边都用，
 * 就会出现"AntD 的 Tag 是亮的、自定义 CSS 的点是暗的"这种对不上的情况。
 */
export const SEMANTIC_COLORS = {
  light: { success: '#18a66a', warning: '#faad14', error: '#ff4d4f' },
  dark: { success: '#49aa19', warning: '#d89614', error: '#dc4446' },
} as const;

/** 默认外观偏好。`SystemSettings` 的"恢复默认"也回到这里。 */
export const DEFAULT_PREFERENCE = {
  appearance: 'system',
  primaryColor: BRAND_COLORS[0].value,
  fontFamily: 'system' as FontFamilyKey,
  fontSize: 14,
  borderRadius: 8,
  density: 'comfortable',
} as const;

/** 从预设里按 key 取字体栈；未知 key 回落到第一项而不是抛错（旧版本存下来的偏好可能对不上）。 */
export function fontStackOf(key: string): string {
  return (FONT_FAMILIES.find((item) => item.key === key) ?? FONT_FAMILIES[0]).stack;
}

/** 判断一个值是不是可用的品牌色。接受任意 6 位 HEX，不再限定在白名单内（公司 VI 色必须能填）。 */
export function isHexColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value);
}
