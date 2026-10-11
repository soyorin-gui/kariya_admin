import { Tooltip } from 'antd';
import './ellipsisText.css';

/**
 * 表格/描述类长文本的统一展示：单行截断，悬浮查看全文，空值统一显示 `-`。
 *
 * 与 antd Table 搭配时应写成 `ellipsis: { showTitle: false }`：
 * `showTitle: false` 关掉单元格原生的 title 提示，避免与这里的 Tooltip 重复弹出。
 */
export function EllipsisText({ value }: { value?: string | number | null }) {
  const text = value === null || value === undefined ? '' : String(value);
  if (!text) return <>-</>;
  return (
    <Tooltip placement='topLeft' styles={{ root: { maxWidth: 520 } }} title={<div className='ellipsis-text-tooltip'>{text}</div>}>
      <span className='ellipsis-text'>{text}</span>
    </Tooltip>
  );
}
