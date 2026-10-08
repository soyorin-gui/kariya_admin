import { useEffect, useRef } from 'react';
import { Spin } from 'antd';
import type { AgentConversationMessage } from '../../types/agent';
import { AgentArtifactView } from './artifacts/AgentArtifactView';
import { AgentMarkdown } from './AgentMarkdown';

interface AiMessageListProps {
  messages: AgentConversationMessage[];
  loading: boolean;
  toolStatus: string | null;
}

/** 对话展示与自动滚动独立于面板状态，避免主组件继续膨胀。 */
export function AiMessageList({ messages, loading, toolStatus }: AiMessageListProps) {
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [loading, messages, toolStatus]);

  return (
    <div className='ai-messages' aria-live='polite'>
      {messages.map((message) =>
        message.text || message.artifacts?.length ? (
          <div className={`bubble ${message.role}`} key={message.id}>
            {message.text && (message.role === 'assistant' ? <AgentMarkdown>{message.text}</AgentMarkdown> : message.text)}
            {message.artifacts?.map((artifact, index) => (
              <AgentArtifactView key={`${artifact.type}-${artifact.schemaVersion}-${index}`} artifact={artifact} />
            ))}
          </div>
        ) : null,
      )}
      {loading && (toolStatus ? <div className='ai-tool-status'>正在调用 {toolStatus} …</div> : <Spin size='small' />)}
      <div ref={endRef} aria-hidden='true' />
    </div>
  );
}
