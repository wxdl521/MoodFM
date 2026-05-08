export function FeedbackBanner({ show, artistName, onAddBlacklist, onDismiss }) {
  if (!show) return null;
  return (
    <div style={{
      position: 'fixed', bottom: 100, left: '50%', transform: 'translateX(-50%)',
      background: 'var(--ink)', color: 'var(--bg)', borderRadius: 24,
      padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 14,
      zIndex: 50, maxWidth: 340, width: 'calc(100% - 40px)',
      boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
    }}>
      <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 13, lineHeight: 1.5 }}>
        连续跳过了 3 首，要屏蔽「{artistName}」吗？
      </div>
      <button onClick={onAddBlacklist} style={{
        background: 'var(--bg)', color: 'var(--ink)', border: 'none',
        borderRadius: 14, padding: '5px 12px', cursor: 'pointer',
        fontFamily: 'var(--serif-cn)', fontSize: 12, flexShrink: 0,
      }}>屏蔽</button>
      <button onClick={onDismiss} style={{
        background: 'transparent', border: 'none', cursor: 'pointer',
        color: 'rgba(255,255,255,0.5)', fontSize: 18, flexShrink: 0, lineHeight: 1,
      }}>✕</button>
    </div>
  );
}
