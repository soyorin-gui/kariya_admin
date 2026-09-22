import { useState } from 'react';
import { Avatar, Button, FloatButton, Input, Spin } from 'antd';
import { CloseOutlined, MessageOutlined, RobotOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import './AiAssistant.css';
interface Message {
  id: number;
  role: 'assistant' | 'user';
  text: string;
}
export function AiAssistant() {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>([{ id: 1, role: 'assistant', text: '你好，我是 Kariya 智能助手。有什么可以帮你？' }]);
  const send = (suggestion?: string) => {
    const value = (suggestion ?? text).trim();
    if (!value || loading) return;
    setMessages((v) => [...v, { id: Date.now(), role: 'user', text: value }]);
    setText('');
    setLoading(true);
    setTimeout(() => {
      setMessages((v) => [...v, { id: Date.now() + 1, role: 'assistant', text: '智能助手服务接口已预留，接入模型后即可为你处理这类请求。' }]);
      setLoading(false);
    }, 650);
  };
  return (
    <>
      <FloatButton icon={<RobotOutlined />} tooltip='AI 智能助手' type='primary' onClick={() => setOpen((value) => !value)} />
      {open && (
        <aside className='ai-workbench' aria-label='AI 智能助手'>
          <div className='ai-workbench-head'>
            <span>
              <MessageOutlined /> 对话助手
            </span>
            <Button type='text' aria-label='关闭 AI 助手' icon={<CloseOutlined />} onClick={() => setOpen(false)} />
          </div>
          <div className='ai-welcome'>
            <div className='ai-welcome-title'>
              <Avatar size={36} icon={<RobotOutlined />} className='ai-avatar' />
              <h2>你好，我是 Kariya 智能助手</h2>
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
            {messages.slice(1).map((m) => (
              <div className={`bubble ${m.role}`} key={m.id}>
                {m.text}
              </div>
            ))}
            {loading && <Spin size='small' />}
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
