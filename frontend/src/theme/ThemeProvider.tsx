import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { DEFAULT_PREFERENCE, FONT_FAMILIES, SEMANTIC_COLORS, fontStackOf, isHexColor, type FontFamilyKey } from './tokens';

export type Appearance = 'system' | 'light' | 'dark';
export type InterfaceDensity = 'comfortable' | 'compact';

export interface ThemePreference {
  appearance: Appearance;
  /** 任意 6 位 HEX。不再限定在 BRAND_COLORS 白名单里，这样公司 VI 色能直接用。 */
  primaryColor: string;
  /** FONT_FAMILIES 里的 key。 */
  fontFamily: FontFamilyKey;
  /** 基础字号（px）。AntD 与自定义 CSS 的整套字号阶梯都由它派生。 */
  fontSize: number;
  /** 圆角（px）。 */
  borderRadius: number;
  density: InterfaceDensity;
}

interface ThemeContextValue extends ThemePreference {
  resolvedAppearance: 'light' | 'dark';
  /**
   * 当前亮暗模式下生效的语义色。
   * <p>
   * 之所以要把它们从 JS 里暴露出来：**ECharts / Canvas 画不出 CSS 变量** ——
   * 它们需要一个真实的颜色字符串。图表里的成功/警告色如果硬编码，
   * 就会出现"切到深色主题后 AntD 的 Tag 变亮了、图表还是老颜色"的不一致。
   */
  semantic: { success: string; warning: string; error: string };
  setAppearance: (value: Appearance) => void;
  setPrimaryColor: (value: string) => void;
  setFontFamily: (value: FontFamilyKey) => void;
  setFontSize: (value: number) => void;
  setBorderRadius: (value: number) => void;
  setDensity: (value: InterfaceDensity) => void;
  reset: () => void;
}

const STORAGE_KEY = 'lbl-ui-preference-v1';
const APPEARANCES: Appearance[] = ['system', 'light', 'dark'];

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * 从 localStorage 读取偏好，**逐字段校验**。
 * <p>
 * 校验而不是直接信任，是因为这份数据跨版本存活：v1 存的可能没有 fontSize 字段，
 * 用户也可能手工改过 localStorage。任一项非法就单独回落默认值 ——
 * 不能因为一个字段坏了就把整份偏好丢掉（那样用户会莫名丢失其他设置）。
 */
function loadPreference(): ThemePreference {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<ThemePreference>;
    return {
      appearance: APPEARANCES.includes(raw.appearance as Appearance) ? (raw.appearance as Appearance) : DEFAULT_PREFERENCE.appearance,
      // ★ 关键：只校验"是不是合法 HEX"，不再要求它在 BRAND_COLORS 里。
      //   旧实现用的是白名单，于是任何自定义色（包括公司 VI 色）在刷新后都会被静默丢弃、弹回默认蓝。
      primaryColor: isHexColor(raw.primaryColor) ? raw.primaryColor : DEFAULT_PREFERENCE.primaryColor,
      fontFamily: FONT_FAMILIES.some((item) => item.key === raw.fontFamily) ? (raw.fontFamily as FontFamilyKey) : DEFAULT_PREFERENCE.fontFamily,
      fontSize: typeof raw.fontSize === 'number' && raw.fontSize >= 12 && raw.fontSize <= 16 ? raw.fontSize : DEFAULT_PREFERENCE.fontSize,
      borderRadius: typeof raw.borderRadius === 'number' && raw.borderRadius >= 0 && raw.borderRadius <= 16 ? raw.borderRadius : DEFAULT_PREFERENCE.borderRadius,
      density: raw.density === 'compact' ? 'compact' : 'comfortable',
    };
  } catch {
    return { ...DEFAULT_PREFERENCE };
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState(loadPreference);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches);
  const resolvedAppearance = preference.appearance === 'system' ? (systemDark ? 'dark' : 'light') : preference.appearance;

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  /**
   * 把偏好投射到 **两套** 目标上。
   *
   * ⚠️ 这是整个主题系统最容易漏的地方：**两套必须同时投**。
   *   - AntD token  → 影响所有 AntD 组件（按钮、表格、输入框、弹窗…）
   *   - CSS 变量    → 影响 `styles/` 与各页面自己写的样式
   * 只投一套就会出现"AntD 的按钮变色了，但页面标题/自定义标签没变"这种半生效状态，
   * 而且不会有任何报错。
   */
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(preference));
    } catch {
      /* 存储不可用时保留当前会话设置（隐私模式 / 配额满）。 */
    }
    const root = document.documentElement;
    const semantic = SEMANTIC_COLORS[resolvedAppearance];
    root.dataset.theme = resolvedAppearance;
    root.dataset.density = preference.density;
    root.style.colorScheme = resolvedAppearance;
    // —— 品牌色 ——（--brand-deep 由 theme.css 用 color-mix 从 --brand 派生，这里不用管）
    root.style.setProperty('--brand', preference.primaryColor);
    // —— 字体与字号 ——
    // 只设 --fs-base：--fs-xs / --fs-sm / --fs-lg … 全部由 theme.css 用 calc() 从它算出来。
    root.style.setProperty('--font-sans', fontStackOf(preference.fontFamily));
    root.style.setProperty('--fs-base', `${preference.fontSize}px`);
    // —— 语义色（亮暗两套，与 AntD 的 darkAlgorithm 给的默认值对齐）——
    root.style.setProperty('--success', semantic.success);
    root.style.setProperty('--warning', semantic.warning);
    root.style.setProperty('--error', semantic.error);
  }, [preference, resolvedAppearance]);

  const semantic = SEMANTIC_COLORS[resolvedAppearance];

  const value = useMemo<ThemeContextValue>(
    () => ({
      ...preference,
      resolvedAppearance,
      semantic: { ...SEMANTIC_COLORS[resolvedAppearance] },
      setAppearance: (appearance) => setPreference((current) => ({ ...current, appearance })),
      setPrimaryColor: (primaryColor) => setPreference((current) => ({ ...current, primaryColor })),
      setFontFamily: (fontFamily) => setPreference((current) => ({ ...current, fontFamily })),
      setFontSize: (fontSize) => setPreference((current) => ({ ...current, fontSize })),
      setBorderRadius: (borderRadius) => setPreference((current) => ({ ...current, borderRadius })),
      setDensity: (density) => setPreference((current) => ({ ...current, density })),
      reset: () => setPreference({ ...DEFAULT_PREFERENCE }),
    }),
    [preference, resolvedAppearance],
  );

  return (
    <ThemeContext.Provider value={value}>
      <ConfigProvider
        locale={zhCN}
        componentSize={preference.density === 'compact' ? 'small' : 'middle'}
        theme={{
          algorithm: resolvedAppearance === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
          // cssVar 模式下 AntD 把 token 输出成 CSS 变量，切换 token 不需要重建 <style>，
          // 这也是"改主题不卡顿"的原因。key 固定成 'app' 让它与 React 的 useId 解耦。
          cssVar: { key: 'app' },
          token: {
            colorPrimary: preference.primaryColor,
            colorSuccess: semantic.success,
            colorWarning: semantic.warning,
            colorError: semantic.error,
            borderRadius: preference.borderRadius,
            // ★ fontFamily 与 fontSize 是"字体、字号全局生效"的关键：
            //   fontSize 是基准，AntD 会据此派生 fontSizeSM / fontSizeLG / fontSizeHeading1..5 等，
            //   所以改这一个值，按钮、表格、表单、标题的字号会一起变。
            fontFamily: fontStackOf(preference.fontFamily),
            fontSize: preference.fontSize,
          },
        }}
      >
        {children}
      </ConfigProvider>
    </ThemeContext.Provider>
  );
}

export function useThemePreference(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) throw new Error('useThemePreference must be used inside ThemeProvider');
  return value;
}

/**
 * 兼容性再导出：`BRAND_COLORS` 的定义已经搬到 `./tokens`（那里才是设计令牌的家），
 * 但历史上有文件从本模块引入它。保留这一行，避免为了搬家去改一圈 import。
 * 新代码请直接从 `../theme/tokens` 引入。
 */
export { BRAND_COLORS } from './tokens';
