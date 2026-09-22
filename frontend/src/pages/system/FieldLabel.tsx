import { QuestionCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';

/**
 * 表单标签 + 说明气泡。
 *
 * 为什么不用 Form.Item 的 extra：
 *   1. extra 渲染在输入框下方，并且 antd 给它设了 min-height，每个带说明的字段都会额外占掉一行；
 *   2. 长说明文字会把该字段撑高，和上下其它字段的行距节奏对不上。
 * 收进 tooltip 之后：每个字段恒定只占一行、弹窗高度可预测，说明内容按需展开。
 *
 * 和 placeholder 的分工：placeholder 负责给"输入示例"（例如 system/user/index），
 * tooltip 负责解释"这个字段是什么规则"，两者不重复。
 *
 * 用法：<Form.Item label={<FieldLabel text='前端组件' hint='……' />} …/>
 */
export function FieldLabel({ text, hint }: { text: string; hint?: string }) {
  if (!hint) return <>{text}</>;
  return (
    <span className='field-label'>
      {text}
      <Tooltip title={hint}>
        <QuestionCircleOutlined className='field-label-hint' />
      </Tooltip>
    </span>
  );
}
