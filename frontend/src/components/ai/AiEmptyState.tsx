import { Avatar } from 'antd';
import { RobotOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { AgentPageContext } from '../../types/agent';

const DEFAULT_SUGGESTIONS = ['介绍一下当前页面可以完成哪些操作', '帮我梳理当前页面的常用操作流程', '使用当前功能时有哪些注意事项'];

interface AiEmptyStateProps {
  pageContext: AgentPageContext;
  onSelectSuggestion: (suggestion: string) => void;
}

/** 只在尚未开始对话时出现的欢迎与快捷提问。 */
export function AiEmptyState({ pageContext, onSelectSuggestion }: AiEmptyStateProps) {
  return (
    <div className='ai-empty-state'>
      <div className='ai-welcome'>
        <div className='ai-welcome-title'>
          <Avatar size={36} icon={<RobotOutlined />} className='ai-avatar' />
          <h2>你好，我是 LBL 智能助手</h2>
        </div>
        <p>
          {pageContext.pageTitle
            ? `我会结合“${pageContext.pageTitle}”页面为你提供帮助。`
            : '欢迎随时提问，我可以协助处理系统操作与管理任务。'}
        </p>
      </div>
      <div className='ai-suggestions' aria-label='推荐问题'>
        {DEFAULT_SUGGESTIONS.map((item) => (
          <button key={item} type='button' onClick={() => onSelectSuggestion(item)}>
            <ThunderboltOutlined />
            {item}
            <span>→</span>
          </button>
        ))}
      </div>
    </div>
  );
}
