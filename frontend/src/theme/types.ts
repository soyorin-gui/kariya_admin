/**
 * 主题相关的派生类型。
 *
 * 目前只有一项：整套设计令牌的联合类型。它由 `./tokens` 的实际导出推导而来，
 * 所以**新增一个令牌会自动进入这个类型**，不需要手工同步。
 */
export type DesignTokens = typeof import('./tokens');
