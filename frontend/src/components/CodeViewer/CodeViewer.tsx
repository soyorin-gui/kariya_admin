import { useEffect, useRef, useState } from 'react';
import { App, Button, Select, Space, Tooltip } from 'antd';
import { DownloadOutlined, FontSizeOutlined, FormatPainterOutlined, FullscreenExitOutlined, FullscreenOutlined, SearchOutlined } from '@ant-design/icons';
import Editor, { type Monaco, type OnMount } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';
import { CopyButton } from '../CopyButton';
import { useFullscreen } from '../../hooks/useFullscreen';
import { downloadBlob } from '../../utils/download';
import './codeViewer.css';

export type CodeLanguage = 'text' | 'json' | 'xml' | 'sql' | 'java' | string;

export interface CodeViewerProps {
  value: string;
  language?: CodeLanguage;
  filename?: string;
  readOnly?: boolean;
  wordWrap?: boolean;
  height?: number | string;
  fontSize?: number;
  showToolbar?: boolean;
  className?: string;
  onChange?: (value: string) => void;
}

function formatXml(value: string): string {
  const lines = value
    .replace(/>\s*</g, '><')
    .replace(/(>)(<)(\/*)/g, '$1\n$2$3')
    .split('\n');
  let depth = 0;
  return lines
    .map((raw) => {
      const line = raw.trim();
      if (/^<\//.test(line)) depth = Math.max(0, depth - 1);
      const formatted = `${'  '.repeat(depth)}${line}`;
      if (/^<[^!?/][^>]*[^/]>/i.test(line) && !/<\/[^>]+>$/.test(line)) depth += 1;
      return formatted;
    })
    .join('\n');
}

export function CodeViewer({
  value,
  language = 'text',
  filename = `code.${language === 'text' ? 'txt' : language}`,
  readOnly = true,
  wordWrap = true,
  height = 420,
  fontSize: initialFontSize = 14,
  showToolbar = true,
  className,
  onChange,
}: CodeViewerProps) {
  const { message } = App.useApp();
  const rootRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<editor.IStandaloneCodeEditor>();
  const monacoRef = useRef<Monaco>();
  const [content, setContent] = useState(value);
  const [fontSize, setFontSize] = useState(initialFontSize);
  const { fullscreen, toggleFullscreen } = useFullscreen(rootRef);

  useEffect(() => setContent(value), [value]);

  const handleMount: OnMount = (instance, monaco) => {
    editorRef.current = instance;
    monacoRef.current = monaco;
  };
  const updateContent = (next: string) => {
    setContent(next);
    onChange?.(next);
  };
  const format = () => {
    try {
      if (language === 'json') updateContent(JSON.stringify(JSON.parse(content), null, 2));
      else if (language === 'xml') updateContent(formatXml(content));
    } catch {
      message.error(`当前内容不是有效的 ${language.toUpperCase()}`);
    }
  };
  const download = () => downloadBlob(new Blob([content], { type: 'text/plain;charset=utf-8' }), filename);
  const resolvedHeight = fullscreen ? '100%' : typeof height === 'number' ? `${height}px` : height;

  return (
    <div ref={rootRef} className={`code-viewer${fullscreen ? ' is-fullscreen' : ''}${className ? ` ${className}` : ''}`}>
      {showToolbar && (
        <div className='code-viewer-toolbar'>
          <Space size={4}>
            <Select
              aria-label='字体大小'
              value={fontSize}
              suffixIcon={<FontSizeOutlined />}
              onChange={setFontSize}
              options={[12, 13, 14, 16, 18, 20].map((size) => ({ value: size, label: `${size}px` }))}
              style={{ width: 92 }}
            />
            <Tooltip title='查找'>
              <Button type='text' icon={<SearchOutlined />} onClick={() => editorRef.current?.getAction('actions.find')?.run()} />
            </Tooltip>
            {(language === 'json' || language === 'xml') && (
              <Tooltip title='格式化'>
                <Button type='text' icon={<FormatPainterOutlined />} onClick={format} />
              </Tooltip>
            )}
          </Space>
          <Space size={4}>
            <CopyButton value={content} />
            <Tooltip title='下载'>
              <Button type='text' icon={<DownloadOutlined />} onClick={download} />
            </Tooltip>
            <Tooltip title={fullscreen ? '退出全屏' : '全屏'}>
              <Button type='text' icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} onClick={() => void toggleFullscreen()} />
            </Tooltip>
          </Space>
        </div>
      )}
      <div className='code-viewer-editor' style={{ height: resolvedHeight }}>
        <Editor
          value={content}
          language={language === 'text' ? 'plaintext' : language}
          onMount={handleMount}
          onChange={(next) => updateContent(next ?? '')}
          options={{
            readOnly,
            wordWrap: wordWrap ? 'on' : 'off',
            fontSize,
            minimap: { enabled: false },
            automaticLayout: true,
            scrollBeyondLastLine: false,
            renderValidationDecorations: readOnly ? 'off' : 'on',
          }}
        />
      </div>
    </div>
  );
}
