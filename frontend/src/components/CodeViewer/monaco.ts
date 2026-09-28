import { loader } from '@monaco-editor/react';

/**
 * Monaco 默认由 @monaco-editor/loader 从 jsDelivr 下载 AMD 文件，离线/内网必然失败。
 * 这里使用随应用发布的 `public/monaco/vs` 静态资源；它们会原样拷入 dist，加载器只访问当前域名。
 * 不直接把完整 Monaco ESM 打入主构建：它包含大量语言 worker，在内网构建机上会显著拉高内存占用。
 */
// Monaco 的 worker 会从 Blob/WorkerGlobalScope 发起二次模块请求；此时 `/monaco/vs` 这类
// 相对站点根路径无法被 worker 的 fetch 解析，必须在主线程先固化为完整 http(s) URL。
// 同时保留 Vite BASE_URL，避免应用挂在 Nginx 子路径（如 /admin/）时丢失前缀。
const monacoVsUrl = new URL(`${import.meta.env.BASE_URL}monaco/vs`, window.location.origin).toString();

loader.config({
  paths: { vs: monacoVsUrl },
  'vs/nls': { availableLanguages: { '*': 'zh-cn' } },
});
