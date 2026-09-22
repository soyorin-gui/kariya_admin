export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className='logo'>
      <span className='logo-mark'>
        <i />
        <b />
        <em />
      </span>
      {!compact && (
        <span>
          <strong>Kariya Admin</strong>
          <small>全栈摸鱼基地</small>
        </span>
      )}
    </div>
  );
}
