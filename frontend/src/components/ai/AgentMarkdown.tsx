import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface AgentMarkdownProps {
  children: string;
}

/** 模型文本按安全 Markdown 渲染；原始 HTML 永远跳过，不启用 rehype-raw。 */
export function AgentMarkdown({ children }: AgentMarkdownProps) {
  return (
    <div className='ai-markdown'>
      <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
        {children}
      </ReactMarkdown>
    </div>
  );
}
