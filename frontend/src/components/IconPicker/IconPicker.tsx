import { CloseCircleFilled, DownOutlined, SearchOutlined } from '@ant-design/icons';
import { Input, Popover, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import './iconPicker.css';

export interface IconPickerOption {
  value: string;
  label?: string;
  icon: ReactNode;
}

interface IconPickerProps {
  value?: string;
  onChange?: (value: string | undefined) => void;
  options: readonly IconPickerOption[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

/** 用网格而非长列表选择图标，可直接作为 antd Form.Item 的字段控件使用。 */
export function IconPicker({
  value,
  onChange,
  options,
  placeholder = '请选择图标',
  disabled = false,
  className,
}: IconPickerProps) {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const selected = options.find((option) => option.value === value);
  const visibleOptions = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (!normalizedKeyword) return options;
    return options.filter((option) => `${option.value} ${option.label ?? ''}`.toLowerCase().includes(normalizedKeyword));
  }, [keyword, options]);

  const close = () => {
    setOpen(false);
    setKeyword('');
  };

  const choose = (nextValue: string) => {
    onChange?.(nextValue);
    close();
  };

  const content = (
    <div className='icon-picker__panel' onClick={(event) => event.stopPropagation()}>
      <div className='icon-picker__search'>
        <Input
          autoFocus
          allowClear
          value={keyword}
          prefix={<SearchOutlined />}
          placeholder='搜索图标名称'
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>
      <div className='icon-picker__grid' role='listbox' aria-label='图标选项'>
        {visibleOptions.length ? visibleOptions.map((option) => (
          <Tooltip key={option.value} title={option.label ?? option.value} mouseEnterDelay={0.35}>
            <button
              className={`icon-picker__option${option.value === value ? ' is-selected' : ''}`}
              type='button'
              role='option'
              aria-selected={option.value === value}
              onClick={() => choose(option.value)}
            >
              <span className='icon-picker__option-icon'>{option.icon}</span>
              <span className='icon-picker__option-name'>{option.label ?? option.value}</span>
            </button>
          </Tooltip>
        )) : <div className='icon-picker__empty'>未找到匹配的图标</div>}
      </div>
    </div>
  );

  return (
    <Popover
      arrow={false}
      classNames={{ root: 'icon-picker-popover' }}
      content={content}
      open={open}
      placement='bottomLeft'
      trigger='click'
      onOpenChange={(nextOpen) => {
        if (disabled) return;
        setOpen(nextOpen);
        if (!nextOpen) setKeyword('');
      }}
    >
      <Input
        className={className}
        disabled={disabled}
        placeholder={placeholder}
        prefix={selected?.icon}
        readOnly
        value={selected?.label ?? selected?.value ?? ''}
        suffix={(
          <span className='icon-picker__suffix'>
            {value && (
              <CloseCircleFilled
                className='icon-picker__clear'
                aria-label='清除图标'
                onMouseDown={(event) => event.preventDefault()}
                onClick={(event) => {
                  event.stopPropagation();
                  onChange?.(undefined);
                }}
              />
            )}
            <DownOutlined className='icon-picker__arrow' />
          </span>
        )}
      />
    </Popover>
  );
}
