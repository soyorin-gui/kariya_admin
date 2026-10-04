import { useEffect, useRef, useState } from 'react';
import { Button, FloatButton, Tooltip } from 'antd';
import { CloseOutlined, MessageOutlined, PlusOutlined, RobotOutlined } from '@ant-design/icons';
import type { AgentConversationMessage, ChatHistoryItem } from '../../types/agent';
import { useAgentChat } from './useAgentChat';
import { useAgentPageContext } from './useAgentPageContext';
import { AiEmptyState } from './AiEmptyState';
import { AiMessageList } from './AiMessageList';
import { AiComposer } from './AiComposer';
import './AiAssistant.css';

/**
 * 面板垂直尺寸约束（单位 px）。
 * <p>
 * 面板锚定在 {@code bottom: BOTTOM_OFFSET}（正好落在悬浮按钮上方），向上生长。
 * 高度上限 = 视口高度 − 底边距 − 顶栏高度 − 顶栏下方预留空隙，
 * 保证面板的顶边**永远不会越过、也不会贴住顶栏** —— 这就是"不要超过标题栏"这条要求的具体化。
 */
const BOTTOM_OFFSET = 96; // 与 .ai-workbench 的 bottom 保持一致
const HEADER_HEIGHT = 66; // 与 layout.css 的 .app-header height 保持一致
const TOP_GAP = 16; // 面板顶边与顶栏之间的最小空隙
const MIN_HEIGHT = 440; // 再矮，欢迎语 + 建议 + 输入框就会挤成一团
const DEFAULT_HEIGHT = 520;

/** 把高度夹到 [MIN_HEIGHT, 视口允许的最大值] 区间内。 */
function clampHeight(value: number): number {
  const max = window.innerHeight - BOTTOM_OFFSET - HEADER_HEIGHT - TOP_GAP;
  return Math.min(Math.max(value, MIN_HEIGHT), max);
}

export function AiAssistant() {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [messages, setMessages] = useState<AgentConversationMessage[]>([]);
  const { send: sendToAgent, cancel, loading, toolStatus } = useAgentChat();
  const pageContext = useAgentPageContext();
  const [height, setHeight] = useState<number>(() => clampHeight(DEFAULT_HEIGHT));
  const [resizing, setResizing] = useState(false);
  const nextMessageId = useRef(Date.now());
  // loading 是异步 state；用同步锁挡住极短时间内的双击，避免第二次请求取消第一次请求。
  const sendingRef = useRef(false);
  // pointermove 是高频事件，起始值必须用 ref 而不是闭包里的 state（state 更新是异步的，
  // 连续 move 之间闭包里的 height 是旧的，高度会"抖动"）。
  const dragState = useRef<{ startY: number; startHeight: number } | null>(null);

  // 旋转屏幕 / 缩放浏览器 / 打开开发者工具时，重新把当前高度夹进合法区间。
  useEffect(() => {
    const onResize = () => setHeight((current) => clampHeight(current));
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const send = async (suggestion?: string) => {
    const value = (suggestion ?? text).trim();
    if (!value || loading || sendingRef.current) return;
    sendingRef.current = true;
    const history: ChatHistoryItem[] = messages.map((message) => ({ role: message.role, content: message.text }));
    const userId = ++nextMessageId.current;
    const assistantId = ++nextMessageId.current;
    setMessages((current) => [...current, { id: userId, role: 'user', text: value }, { id: assistantId, role: 'assistant', text: '' }]);
    setText('');
    try {
      const reply = await sendToAgent({
        message: value,
        history,
        pageContext,
        onDelta: (fullText) => {
          setMessages((current) => current.map((message) => (message.id === assistantId ? { ...message, text: fullText } : message)));
        },
        onArtifact: (artifact) => {
          setMessages((current) =>
            current.map((message) => (message.id === assistantId ? { ...message, artifacts: [...(message.artifacts ?? []), artifact] } : message)),
          );
        },
      });
      setMessages((current) => current.map((message) => (message.id === assistantId ? { ...message, text: reply } : message)));
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        // 停止生成时保留已经收到的部分内容；若还没有任何输出，则移除空的助手气泡。
        setMessages((current) => current.filter((message) => message.id !== assistantId || message.text || message.artifacts?.length));
        return;
      }
      const err = e instanceof Error ? e.message : '请求失败，请稍后重试';
      setMessages((current) => current.map((message) => (message.id === assistantId ? { ...message, text: message.text || err } : message)));
    } finally {
      sendingRef.current = false;
    }
  };

  const newConversation = () => {
    cancel();
    setMessages([]);
    setText('');
  };

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    // setPointerCapture：拖拽过程中即使指针移出手柄（甚至移出浏览器窗口），
    // pointermove/pointerup 仍会派发给这个手柄，拖拽不会中途"断掉"。
    event.currentTarget.setPointerCapture(event.pointerId);
    dragState.current = { startY: event.clientY, startHeight: height };
    setResizing(true);
    // 拖拽时禁止文本选择，否则拖动经过文字会高亮一大片。
    document.body.style.userSelect = 'none';
  };

  const moveResize = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragState.current;
    if (!drag) return;
    // 向上拖（clientY 变小）→ 高度增大；向下拖 → 高度减小。
    setHeight(clampHeight(drag.startHeight + (drag.startY - event.clientY)));
  };

  const endResize = (event: React.PointerEvent<HTMLDivElement>) => {
    dragState.current = null;
    setResizing(false);
    document.body.style.userSelect = '';
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  return (
    <>
      <FloatButton icon={<RobotOutlined />} tooltip='AI 智能助手' type='primary' onClick={() => setOpen((value) => !value)} />
      {open && (
        <aside className={`ai-workbench${resizing ? ' resizing' : ''}`} aria-label='AI 智能助手' style={{ height }}>
          {/* 顶部整条都是拖拽热区；中间的短横线只是视觉提示。cursor 交给 CSS。 */}
          <div className='ai-resize-handle' aria-hidden='true' onPointerDown={startResize} onPointerMove={moveResize} onPointerUp={endResize} onPointerCancel={endResize} />
          <div className='ai-workbench-head'>
            <span>
              <MessageOutlined /> 对话助手
            </span>
            <div className='ai-workbench-actions'>
              <Tooltip title='新建对话'>
                <Button type='text' aria-label='新建对话' icon={<PlusOutlined />} disabled={!messages.length && !text} onClick={newConversation} />
              </Tooltip>
              <Tooltip title='关闭'>
                <Button
                  type='text'
                  aria-label='关闭 AI 助手'
                  icon={<CloseOutlined />}
                  onClick={() => {
                    cancel();
                    setOpen(false);
                  }}
                />
              </Tooltip>
            </div>
          </div>
          {messages.length === 0 ? (
            <AiEmptyState pageContext={pageContext} onSelectSuggestion={(suggestion) => void send(suggestion)} />
          ) : (
            <AiMessageList messages={messages} loading={loading} toolStatus={toolStatus} />
          )}
          <AiComposer value={text} loading={loading} pageTitle={pageContext.pageTitle} onChange={setText} onSend={() => void send()} onStop={cancel} />
        </aside>
      )}
    </>
  );
}
