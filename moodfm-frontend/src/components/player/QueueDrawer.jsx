import { usePlayerStore } from '../../store/playerStore';

export function QueueDrawer({ open, onClose }) {
  const { queue, currentIndex } = usePlayerStore();
  const upcoming = queue.slice(currentIndex + 1, currentIndex + 21);

  if (!open) return null;

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, zIndex: 100,
        background: 'rgba(0,0,0,0.55)',
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
      }}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: 'var(--bg)', borderRadius: '20px 20px 0 0',
          padding: '20px 20px 48px',
          maxHeight: '65vh', overflowY: 'auto',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div className="mono" style={{ fontSize: 11, letterSpacing: '.16em', opacity: 0.45 }}>
            UP NEXT · 接下来 {upcoming.length > 0 ? `· ${upcoming.length} 首` : ''}
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', opacity: 0.4, fontSize: 18, lineHeight: 1 }}>✕</button>
        </div>

        {upcoming.length === 0 ? (
          <div style={{ textAlign: 'center', paddingTop: 24, opacity: 0.35, fontFamily: 'var(--serif-cn)', fontSize: 14 }}>队列为空</div>
        ) : (
          upcoming.map((s, i) => (
            <div key={s.id ?? i} style={{
              display: 'flex', alignItems: 'center', gap: 14,
              padding: '10px 0', borderBottom: '1px solid var(--rule)',
            }}>
              <span className="mono" style={{ width: 24, textAlign: 'right', opacity: 0.35, fontSize: 12, flexShrink: 0 }}>
                {currentIndex + i + 2}
              </span>
              <div style={{ width: 40, height: 40, borderRadius: 6, background: 'var(--paper)', overflow: 'hidden', flexShrink: 0, border: '1px solid var(--rule)' }}>
                {s.coverUrl
                  ? <img src={s.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.15 }}>♪</div>
                }
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.title}</div>
                <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{s.artist}</div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
