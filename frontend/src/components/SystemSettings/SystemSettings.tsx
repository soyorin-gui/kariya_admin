import { Button, ColorPicker, Drawer, Radio, Segmented, Select, Space, Tooltip } from 'antd';
import { CheckOutlined, DesktopOutlined, MoonOutlined, ReloadOutlined, SunOutlined } from '@ant-design/icons';
import { useThemePreference, type Appearance } from '../../theme/ThemeProvider';
import { BORDER_RADII, BRAND_COLORS, FONT_FAMILIES, FONT_SIZES, isHexColor } from '../../theme/tokens';
import './systemSettings.css';

/**
 * 外观设置面板。
 *
 * <h2>这里的每一项都同时作用于 AntD 组件与自定义 CSS</h2>
 * 投射逻辑在 `theme/ThemeProvider.tsx` 的那个 useEffect 里（AntD token + CSS 变量两套都投）。
 * 所以在这个面板里改任何一项，按钮、表格、表单这些 AntD 组件和你自己写的页面样式会**一起**变。
 *
 * <h2>新增一项时要改哪 4 处</h2>
 * 见 `theme/tokens.ts` 顶部注释的"新增一个可切换的外观项要改哪 4 处"。
 */
export function SystemSettings({ open, onClose }: { open: boolean; onClose: () => void }) {
  const theme = useThemePreference();
  const appearanceOptions: Array<{ value: Appearance; label: string; icon: React.ReactNode }> = [
    { value: 'system', label: '跟随系统', icon: <DesktopOutlined /> },
    { value: 'light', label: '浅色', icon: <SunOutlined /> },
    { value: 'dark', label: '深色', icon: <MoonOutlined /> },
  ];

  return (
    <Drawer
      title='系统设置'
      placement='right'
      width={380}
      open={open}
      onClose={onClose}
      extra={
        <Button type='text' icon={<ReloadOutlined />} onClick={theme.reset}>
          恢复默认
        </Button>
      }
    >
      <section className='setting-section'>
        <h3>外观模式</h3>
        <p>跟随系统会在操作系统切换外观时自动同步。</p>
        <Segmented block value={theme.appearance} onChange={(value) => theme.setAppearance(value as Appearance)} options={appearanceOptions} />
      </section>

      <section className='setting-section'>
        <h3>品牌主题色</h3>
        <p>影响按钮、链接、选中态等所有强调色，以及页面里自定义样式的强调色。</p>
        <Space size={14} wrap>
          {BRAND_COLORS.map((color) => (
            <Tooltip title={color.name} key={color.value}>
              <button
                type='button'
                className='theme-color-swatch'
                style={{ backgroundColor: color.value }}
                aria-label={`使用${color.name}主题`}
                aria-pressed={theme.primaryColor === color.value}
                onClick={() => theme.setPrimaryColor(color.value)}
              >
                {theme.primaryColor === color.value && <CheckOutlined />}
              </button>
            </Tooltip>
          ))}
        </Space>
        {/* 任意颜色：预设之外还想用公司 VI 色时用这个。
            旧实现把可选色写成了白名单，任何自定义色在刷新后都会被静默丢弃、弹回默认蓝。 */}
        <div className='setting-inline'>
          <span>自定义颜色</span>
          <ColorPicker
            value={theme.primaryColor}
            disabledAlpha
            showText
            onChangeComplete={(color) => {
              const hex = color.toHexString().toUpperCase();
              if (isHexColor(hex)) theme.setPrimaryColor(hex);
            }}
          />
        </div>
      </section>

      <section className='setting-section'>
        <h3>字体</h3>
        <p>同时作用于按钮、表格等组件与页面自有文案。</p>
        <Select
          style={{ width: '100%' }}
          value={theme.fontFamily}
          onChange={theme.setFontFamily}
          options={FONT_FAMILIES.map((item) => ({
            value: item.key,
            label: <span style={{ fontFamily: item.stack }}>{item.name}</span>,
          }))}
        />
      </section>

      <section className='setting-section'>
        <h3>基础字号</h3>
        <p>整站字号由这一个值派生，页面标题、表格、按钮会按比例一起变化。</p>
        <Radio.Group value={theme.fontSize} onChange={(event) => theme.setFontSize(event.target.value)} optionType='button' buttonStyle='solid'>
          {FONT_SIZES.map((size) => (
            <Radio.Button key={size} value={size}>
              {size}px
            </Radio.Button>
          ))}
        </Radio.Group>
      </section>

      <section className='setting-section'>
        <h3>圆角</h3>
        <p>按钮、输入框、卡片、弹窗统一使用同一个圆角值。</p>
        <Segmented
          block
          value={theme.borderRadius}
          onChange={(value) => theme.setBorderRadius(value as number)}
          options={BORDER_RADII.map((radius) => ({ value: radius, label: `${radius}` }))}
        />
      </section>

      <section className='setting-section'>
        <h3>界面密度</h3>
        <p>紧凑模式适合需要同时查看更多数据的场景。注意它只调整控件的尺寸与间距，不改变字号。</p>
        <Radio.Group value={theme.density} onChange={(event) => theme.setDensity(event.target.value)}>
          <Radio.Button value='comfortable'>舒适</Radio.Button>
          <Radio.Button value='compact'>紧凑</Radio.Button>
        </Radio.Group>
      </section>

      <div className='setting-current'>当前生效：{theme.resolvedAppearance === 'dark' ? '深色模式' : '浅色模式'}</div>
    </Drawer>
  );
}
