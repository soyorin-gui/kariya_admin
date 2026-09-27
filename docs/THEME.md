# 主题与外观系统：怎么用、怎么改、为什么以前改不动

> 这一篇回答三件事：
> **① 作为使用者怎么用** → **② 作为开发者怎么改默认值** → **③ 为什么你之前改了没效果**（以及怎么避免再踩）。

---

## 一、怎么用（不需要改代码）

登录后点**右上角的齿轮图标** → 打开「系统设置」抽屉。里面 6 项：

| 设置项 | 作用范围 | 说明 |
| --- | --- | --- |
| **外观模式** | 全站 | 跟随系统 / 浅色 / 深色。"跟随系统"会监听操作系统的切换并实时同步 |
| **品牌主题色** | 全站 | 5 个预设 + **一个取色器可填任意颜色**（公司 VI 色直接用）。影响按钮、链接、选中态、进度条、图表、以及自定义 CSS 里所有强调色 |
| **字体** | 全站 | 系统默认 / 无衬线 / 衬线 / 等宽。**同时**影响 AntD 组件（按钮、表格、表单）和页面自有文案 |
| **基础字号** | 全站 | 12 / 13 / 14 / 15 / 16 px。**整站字号由这一个值派生** —— 页面标题、表格、按钮、卡片会按比例一起变 |
| **圆角** | 全站 | 0 / 4 / 6 / 8 / 12。按钮、输入框、卡片、弹窗统一使用 |
| **界面密度** | AntD 组件 | 舒适 / 紧凑。只调整控件尺寸与间距，**不改变字号** |

**所有设置立即生效、无需刷新，并自动保存到 localStorage**（key：`lbl-ui-preference-v1`）。
右上角「恢复默认」把它们全部重置。

---

## 二、作为开发者：改默认值只改一个文件

**`frontend/src/theme/tokens.ts`** —— 这是全项目默认外观的**唯一定义处**。

| 想改什么 | 改哪里 |
| --- | --- |
| 默认品牌色 | `DEFAULT_PREFERENCE.primaryColor`（或调整 `BRAND_COLORS` 预设列表） |
| 默认字体 | `DEFAULT_PREFERENCE.fontFamily`（对应 `FONT_FAMILIES` 的 key） |
| 默认字号 | `DEFAULT_PREFERENCE.fontSize` |
| 默认圆角 | `DEFAULT_PREFERENCE.borderRadius` |
| 可选的品牌色预设 | `BRAND_COLORS` |
| 可选的字体列表 | `FONT_FAMILIES`（加一项，`SystemSettings` 的下拉自动出现） |
| 可选的字号档位 | `FONT_SIZES`（`loadPreference` 的范围校验也要同步改） |
| 成功/警告/错误色 | `SEMANTIC_COLORS`（**亮暗两套都要给**） |
| 中性色（背景/文字/边框/阴影） | `frontend/src/styles/theme.css` 的 `:root` 与 `[data-theme='dark']` |

改完**刷新页面**即可看到效果（默认值只在没有本地偏好时生效；如果之前点过设置，先点「恢复默认」）。

---

## 三、⭐ 为什么你之前改了没效果（4 个原因）

这是我这次真正要解决的问题。你当时的做法很可能是去改 `styles/theme.css` 或 `theme/tokens.ts` —— 这是最自然的选择，但**这两处都被运行时覆盖了**：

### 原因 1：改 `styles/theme.css` 的 `--brand` 无效

```css
/* theme.css —— 你改这里，永远不生效 */
:root { --brand: #1677ff; }
```

因为 `ThemeProvider` 在运行时执行：

```ts
document.documentElement.style.setProperty('--brand', preference.primaryColor);
```

这写的是 `<html>` 元素的 **inline style**，而 **inline style 的优先级高于任何样式表**（除非样式表用 `!important`）。
所以 `theme.css` 里那个值**只在 React 挂载前生效**（也就是首屏那几十毫秒）。

### 原因 2：改旧 `tokens.ts` 的 `colorPrimary` 无效

旧代码是：

```ts
theme={{ token: { ...themeTokens, colorPrimary: preference.primaryColor } }}
```

`colorPrimary` 被后面那个键**覆盖**了 —— JS 对象展开时后面的键赢。所以 `themeTokens.colorPrimary` 里的值**永远是死的**。

### 原因 3：字体和字号根本没有开关

旧的 `themeTokens` 里**没有 font-size**，`SystemSettings` 里也**没有字体/字号控件**。
`componentSize`（密度）能改控件高度，但改不了字号 —— 这是两件事。

### 原因 4：自定义 CSS 里有 43 处写死的字号、99 处写死的颜色

即使主题变了，这些地方也不会动。举几个你一定会看到的例子：

```css
/* 改之前 */
body { font-family: Inter, 'PingFang SC', ...; }   /* reset.css —— 换字体对它无效 */
.page-kicker { font-size: 12px; }                  /* 换字号对它无效 */
#app-loading .app-loading-dots i { background: #1677ff; }  /* 换品牌色对它无效 */
.stat-card small { color: #18a66a; }               /* 换语义色对它无效 */
.tree-expand-arrow { color: #5f90df; }             /* 换品牌色对它无效 */
```

**这四个原因叠加的结果就是"我改了，但没什么变化"。**

### 本次是怎么修的

| 修法 | 具体做法 |
| --- | --- |
| 消除原因 1、2 | `tokens.ts` 成为唯一定义处；`theme.css` 里的值降级为"首屏兜底"并在注释里写明；`loadPreference` 不再用白名单限制品牌色 |
| 消除原因 3 | `ThemePreference` 新增 `fontFamily` / `fontSize` / `borderRadius`；`SystemSettings` 新增 3 个控件 + 一个任意色取色器 |
| 消除原因 4 | 43 处写死字号 → `var(--fs-*)` 阶梯；`#1677ff` / `#18a66a` / `#faad14` / `#ff4d4f` / `#5f90df` → 主题变量；`body` 的字体与字号 → `var(--font-sans)` / `var(--fs-base)` |
| 关键机制 | `ThemeProvider` 的 effect **同时**投射到 **AntD token** 和 **CSS 变量**（见第四节） |

---

## 四、核心机制：一次偏好 → 投射到两套目标

```
                    ┌──────────────────────────────────────────┐
   用户偏好          │  ThemeProvider 的 useEffect              │
   (localStorage)    │                                          │
        │            │  ① AntD ConfigProvider.theme.token       │
        ▼            │     colorPrimary / fontFamily / fontSize  │
  ThemePreference ──▶│     borderRadius / colorSuccess…          │
        │            │        ↓                                  │
        │            │     所有 AntD 组件：按钮、表格、表单、弹窗  │
        │            │                                          │
        │            │  ② document.documentElement.style         │
        │            │     --brand / --font-sans / --fs-base      │
        │            │     --success / --warning / --error       │
        │            │        ↓                                  │
        └───────────▶│     所有自己写的 CSS：styles/ + 各页面     │
                     └──────────────────────────────────────────┘
```

**⚠️ 这两套必须同时投。** 只投一套就会出现"AntD 的按钮变色了，但页面标题/自定义标签没变"这种**半生效**状态，而且**不会有任何报错** —— 这是最耗时的一类问题。

> **为什么 AntD 的组件不能直接读我们的 CSS 变量？**
> 因为 AntD 的组件样式由它的 CSS-in-JS 引擎生成，它只认自己的 token 体系。
> 反过来，我们自己的 CSS 也读不到 AntD 的内部变量（名字会带 hash）。所以两边都必须喂一次。

**字号是怎么"一处改、全站动"的**：`--fs-base` 是唯一的输入，其余 11 个字号在 `theme.css` 里用 `calc()` 从它派生；AntD 那边只喂 `token.fontSize`，它的 `fontSizeSM` / `fontSizeLG` / `fontSizeHeading1..5` 由算法自动派生。

---

## 五、CSS 变量速查表（写新样式时用这些，不要写死）

### 字号阶梯（全部从 `--fs-base` 派生）

| 变量 | 默认值（base=14） | 用在哪 | 当前用了多少处 |
| --- | --- | --- | --- |
| `--fs-xs` | `base - 2px` = 12px | 辅助说明、小标签 | 4 |
| **`--fs-sm`** | `base - 1px` = 13px | **次要正文、表格单元（最常用）** | **18** |
| `--fs-base` | 14px | 正文（一般靠 body 继承，不必显式写） | 4 |
| `--fs-lg` | `base + 2px` = 16px | 卡片标题 | 8 |
| `--fs-xl` | `base + 4px` = 18px | 区块标题 | 5 |
| `--fs-2xl` | `base + 8px` = 22px | 页面标题 | 4 |
| `--fs-3xl` | `base + 10px` = 24px | 页面大标题 | 3 |
| `--fs-display` | `base * 2.3` ≈ 32px | 看板数字 | 1 |
| `--fs-hero` | `base * 3` = 42px | 错误页主标题 | 1 |
| `--fs-huge` | `base * 4` = 56px | 错误页大字 | 1 |
| `--fs-mega` | `base * 4.6` ≈ 64px | 登录页主标题 | 1 |

> 展示型字号（display 及以上）刻意用**倍数**而不是加减：这样把基础字号从 12 调到 16 时，大标题仍保持比例感，不会显得"标题没怎么变、正文变了很多"。

### 颜色与字体

| 变量 | 说明 |
| --- | --- |
| `--brand` | 品牌主色。**任何强调色都用它** |
| `--brand-deep` | 深色变体（hover / 渐变 / 阴影）。由 `color-mix` 从 `--brand` 自动派生，**不要手写** |
| `--success` / `--warning` / `--error` | 语义色，亮暗两套由 JS 切换 |
| `--font-sans` | 全站字体栈 |
| `--ink` / `--ink-soft` / `--muted` | 主/次/弱文字色 |
| `--line` | 边框与分隔线 |
| `--page` / `--surface` / `--surface-soft` / `--surface-elevated` | 页面底 / 卡片 / 次级面 / 浮层 |
| `--shadow` | 阴影色 |

### 让颜色跟随品牌色的写法

```css
/* ✅ 用变量 */
.card-title { color: var(--brand); }
/* ✅ 需要"浅一点的品牌色"时，用 color-mix 派生，而不是手写一个近似的蓝 */
.arrow { color: color-mix(in srgb, var(--brand) 72%, var(--surface)); }
/* ✅ 需要半透明品牌色时（背景/光晕） */
.glow { background: color-mix(in srgb, var(--brand) 12%, transparent); }

/* ❌ 不要这样：换主题时它不会变 */
.card-title { color: #1677ff; }
```

`color-mix` 在本项目里已经在用（原本就有），浏览器支持不是新增成本。

### 图表（ECharts）怎么写

**ECharts 画在 Canvas 上，既读不到 CSS 变量，也不认 `color-mix()`** —— 它需要一个真实的颜色字符串。
所以图表颜色要从 `ThemeProvider` 的 context 里取，而不是写死：

```tsx
import { useThemePreference } from '../../theme/ThemeProvider';

function MyChart() {
  const { primaryColor, semantic } = useThemePreference();
  const option = useMemo(() => ({
    series: [{ type: 'line', lineStyle: { color: primaryColor } }],
  }), [primaryColor]);           // ★ 依赖里必须带上颜色，否则换主题不重绘

  return <BaseChart option={option} />;
}
```

需要带透明度的渐变时，用 `home/index.tsx` 里那个 `withAlpha(hex, alpha)` 小工具把 HEX 转成 `rgba()`。

---

## 六、新增一个可切换的外观项：要改哪 4 处

假设你想加一个「表格行高」开关：

| # | 文件 | 改什么 |
| --- | --- | --- |
| 1 | `theme/tokens.ts` | 加选项列表 + 加进 `DEFAULT_PREFERENCE` |
| 2 | `theme/ThemeProvider.tsx` | ① 加进 `ThemePreference`；② 加进 `loadPreference` 的校验；③ **加进那个 effect，而且 AntD token 与 CSS 变量两套都要投**；④ 在 context 里加 setter |
| 3 | `styles/theme.css` | 加对应的 CSS 变量（给一个合理的兜底值） |
| 4 | `components/SystemSettings/SystemSettings.tsx` | 加控件 |

**第 2 步的 ③ 是最容易漏的** —— 只投一套就是"半生效"。

---

## 七、已知的"不跟随主题"清单（有意保留）

这几处**刻意**不跟主题走，看到它们不用当成 bug：

| 位置 | 为什么不跟 |
| --- | --- |
| `pages/login/login.css`（约 25 处颜色） | 登录页是一套"浅色设计 + 背景图"的独立视觉。**换品牌时你会整页重做**，现在改它性价比低 |
| `pages/error/notFound.css` 的 `clamp(72px, 10vw, 122px)` 等 2 处 | 404 页的大字是**视口驱动**的装饰元素，与正文字号无关 |
| `pages/account/security/security.css` 的 `--provider-*` | 微信/ GitHub / Google 的**平台品牌色**，不是我们的主题色（深色下 GitHub 那项有单独覆盖） |
| `pages/home/index.tsx` 的 `ACCENT_PURPLE` | 多系列图表需要的"第二色"，固定不变 |

**深色主题的覆盖情况**：修完 `loadingScreen.css`、`index.html` 内联 loading、`security.css`、`pageUnavailable.css` 之后，
现在**只剩登录页 / 注册页 / 404 页**是浅色专属设计。这三页都是"未登录或异常"场景，且都计划在换品牌时重做。

---

## 八、验证主题是否真的全局生效（30 秒自测）

打开「系统设置」，按顺序做这 4 个动作，**每一项都应立刻全站变化**：

| 动作 | 应该看到什么（同时变化才算通过） |
| --- | --- |
| 品牌色改成**珊瑚橙** | ① 主按钮底色；② 侧边栏选中项；③ 页面左上角 `OVERVIEW` 那种小标签；④ 顶栏品牌 logo 旁的文字高亮；⑤ 首页折线图的线与渐变；⑥ **点一次查询时的顶部进度条** |
| 字体改成**等宽** | ① 表格内容；② 表单 label；③ 侧边栏菜单；④ **首屏 loading 的那句文案**（刷新页面看） |
| 字号改成 **16px** | ① 页面标题明显变大；② 表格行变高；③ 按钮文字变大；④ 看板数字变大 |
| 圆角改成 **0** | ① 按钮；② 输入框；③ 卡片；④ 弹窗；⑤ 设置面板自己 |

**如果某一项"AntD 变了但页面自有样式没变"** → 说明那个自定义样式里还写死了值，按第五节把它换成变量。
**如果"只有页面自有样式变了、AntD 没变"** → 说明 `ThemeProvider` 的 token 投射漏了这一项。

---

## 九、相关文件索引

| 文件 | 职责 |
| --- | --- |
| `src/theme/tokens.ts` | ⭐ 设计令牌唯一定义处（预设、默认值、选项列表） |
| `src/theme/ThemeProvider.tsx` | ⭐ 偏好状态 + 投射到 AntD token 与 CSS 变量 |
| `src/styles/theme.css` | ⭐ CSS 侧变量（中性色在此独占；字号阶梯在此派生） |
| `src/components/SystemSettings/SystemSettings.tsx` | 设置面板 UI |
| `src/styles/reset.css` | body 的字体与基础字号（走变量） |
| `src/styles/global.css` | 全局类（`.page-head` / `.page-title` / …）+ nprogress 品牌色适配 |
| `src/theme/index.ts` | barrel 出口（可用可不用） |
