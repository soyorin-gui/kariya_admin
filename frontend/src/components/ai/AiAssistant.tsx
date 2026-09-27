import { useEffect, useRef, useState } from 'react';
import { Avatar, Button, FloatButton, Input, Spin } from 'antd';
import { CloseOutlined, MessageOutlined, RobotOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { ChatHistoryItem } from '../../types/agent';
import { useAgentChat } from './useAgentChat';
import './AiAssistant.css';

interface Message {
  id: number;
  role: 'assistant' | 'user';
  text: string;
}

/** 欢迎语消息的固定 id，用于把它从多轮历史里排除（问候语不算对话历史）。 */
const GREETING_ID = 1;

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
  const [messages, setMessages] = useState<Message[]>([{ id: GREETING_ID, role: 'assistant', text: '你好，我是 LBL 智能助手。有什么可以帮你？' }]);
  const { send: sendToAgent, loading, toolStatus } = useAgentChat();
  const [height, setHeight] = useState<number>(() => clampHeight(DEFAULT_HEIGHT));
  const [resizing, setResizing] = useState(false);
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
    if (!value || loading) return;
    // 多轮历史：排除欢迎语，把现有消息转成后端要的 {role, content}。
    const history: ChatHistoryItem[] = messages.filter((m) => m.id !== GREETING_ID).map((m) => ({ role: m.role, content: m.text }));
    setMessages((v) => [...v, { id: Date.now(), role: 'user', text: value }]);
    setText('');
    const assistantId = Date.now() + 1;
    setMessages((v) => [...v, { id: assistantId, role: 'assistant', text: '' }]);
    try {
      const reply = await sendToAgent(value, history, (fullText) => {
        setMessages((v) => v.map((m) => (m.id === assistantId ? { ...m, text: fullText } : m)));
      });
      setMessages((v) => v.map((m) => (m.id === assistantId ? { ...m, text: reply } : m)));
    } catch (e) {
      const err = e instanceof Error ? e.message : '请求失败，请稍后重试';
      setMessages((v) => v.map((m) => (m.id === assistantId ? { ...m, text: m.text || err } : m)));
    }
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
            <Button type='text' aria-label='关闭 AI 助手' icon={<CloseOutlined />} onClick={() => setOpen(false)} />
          </div>
          <div className='ai-welcome'>
            <div className='ai-welcome-title'>
              <Avatar size={36} icon={<RobotOutlined />} className='ai-avatar' />
              <h2>你好，我是 LBL 智能助手</h2>
            </div>
            <p>欢迎随时提问，我可以协助处理系统操作与管理任务。</p>
          </div>
          <div className='ai-suggestions'>
            {['帮我查看当前用户的权限配置', '如何新增一个系统角色？', '帮我整理本周的登录情况'].map((item) => (
              <button key={item} onClick={() => send(item)}>
                <ThunderboltOutlined />
                {item}
                <span>→</span>
              </button>
            ))}
          </div>
          <div className='ai-messages'>
            {messages.slice(1).map((m) =>
              m.text ? (
                <div className={`bubble ${m.role}`} key={m.id}>
                  {m.text}
                </div>
              ) : null,
            )}
            {loading && (toolStatus ? <div className='ai-tool-status'>正在调用 {toolStatus} …</div> : <Spin size='small' />)}
          </div>
          <div className='ai-input'>
            <Input.TextArea
              value={text}
              onChange={(e) => setText(e.target.value)}
              onPressEnter={(e) => {
                if (!e.shiftKey) {
                  e.preventDefault();
                  send();
                }
              }}
              placeholder='输入你想了解的问题…'
              autoSize={{ minRows: 2, maxRows: 4 }}
            />
            <Button type='primary' aria-label='发送' icon={<SendOutlined />} onClick={() => send()} />
          </div>
        </aside>
      )}
    </>
  );
}
