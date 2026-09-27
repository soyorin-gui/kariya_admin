/**
 * 主题模块的 barrel 出口。
 *
 * ⚠️ 历史上这个文件是**没人使用**的 —— 所有消费者都直接引 `./ThemeProvider` 或 `./tokens`。
 * 保留它是为了给"以后想统一入口"留个位置；**新代码可以继续直接引具体模块**，
 * 两种方式都行，不强制。
 */
export {
  BRAND_COLORS,
  BORDER_RADII,
  DEFAULT_PREFERENCE,
  FONT_FAMILIES,
  FONT_SIZES,
  SEMANTIC_COLORS,
  fontStackOf,
  isHexColor,
} from './tokens';
export type { FontFamilyKey } from './tokens';
export { ThemeProvider, useThemePreference } from './ThemeProvider';
export type { Appearance, InterfaceDensity, ThemePreference } from './ThemeProvider';
