/* global React */
// Shared atoms for MoodFM screens

const { useState, useEffect, useRef } = React;

/* ---------- Mood Blob (animated gradient cover) ---------- */
function MoodBlob({ size = 280, drift = true, style = {}, geometry = 'blob', children }) {
  const id = React.useId();
  const radius = geometry === 'square' ? 16 : geometry === 'circle' ? size / 2 : 0;

  if (geometry === 'blob') {
    return (
      <div style={{ width: size, height: size, position: 'relative', ...style }}>
        <svg viewBox="0 0 200 200" width={size} height={size} style={{ display: 'block' }}>
          <defs>
            <radialGradient id={`g1-${id}`} cx="30%" cy="30%" r="80%">
              <stop offset="0%" stopColor="var(--mood-a)" />
              <stop offset="100%" stopColor="var(--mood-c)" />
            </radialGradient>
            <radialGradient id={`g2-${id}`} cx="70%" cy="70%" r="80%">
              <stop offset="0%" stopColor="var(--mood-b)" stopOpacity="0.9" />
              <stop offset="100%" stopColor="var(--mood-d)" stopOpacity="0" />
            </radialGradient>
            <filter id={`f-${id}`}>
              <feGaussianBlur stdDeviation="0.6" />
            </filter>
          </defs>
          <path
            d="M 100,18
               C 145,18 180,55 180,100
               C 180,150 145,182 100,182
               C 55,182 22,150 22,100
               C 22,58 55,18 100,18 Z"
            fill={`url(#g1-${id})`}
            filter={`url(#f-${id})`}
          >
            {drift && (
              <animate
                attributeName="d"
                dur="14s"
                repeatCount="indefinite"
                values="
                  M 100,18 C 145,18 180,55 180,100 C 180,150 145,182 100,182 C 55,182 22,150 22,100 C 22,58 55,18 100,18 Z;
                  M 100,22 C 152,15 178,62 184,108 C 188,148 138,184 96,178 C 50,172 18,142 22,96 C 26,52 60,28 100,22 Z;
                  M 100,18 C 145,18 180,55 180,100 C 180,150 145,182 100,182 C 55,182 22,150 22,100 C 22,58 55,18 100,18 Z"
              />
            )}
          </path>
          <path
            d="M 100,30 C 140,30 170,60 170,100 C 170,140 140,170 100,170 C 60,170 30,140 30,100 C 30,60 60,30 100,30 Z"
            fill={`url(#g2-${id})`}
            opacity="0.7"
          >
            {drift && (
              <animateTransform
                attributeName="transform"
                type="translate"
                dur="11s"
                values="0,0; 8,-6; -6,8; 0,0"
                repeatCount="indefinite"
              />
            )}
          </path>
        </svg>
        {children && <div style={{ position: 'absolute', inset: 0 }}>{children}</div>}
      </div>
    );
  }

  // circle / square — flat gradient surface
  return (
    <div
      style={{
        width: size, height: size, borderRadius: radius,
        background: `
          radial-gradient(circle at 28% 28%, var(--mood-a) 0%, transparent 55%),
          radial-gradient(circle at 75% 35%, var(--mood-b) 0%, transparent 55%),
          radial-gradient(circle at 50% 85%, var(--mood-c) 0%, transparent 60%),
          radial-gradient(circle at 18% 75%, var(--mood-d) 0%, transparent 60%)`,
        backgroundColor: 'var(--mood-c)',
        position: 'relative',
        ...style,
      }}
    >{children}</div>
  );
}

/* ---------- MoodWheel — 2D slider ---------- */
function MoodWheel({ size = 320, geometry = 'circle', value, onChange }) {
  const ref = useRef(null);
  const [pos, setPos] = useState(value || { x: 0.62, y: 0.32 });
  const [drag, setDrag] = useState(false);

  useEffect(() => { onChange && onChange(pos); }, [pos]);

  const startDrag = (e) => {
    setDrag(true);
    moveDrag(e);
  };
  const moveDrag = (e) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const cx = (e.touches ? e.touches[0].clientX : e.clientX) - rect.left;
    const cy = (e.touches ? e.touches[0].clientY : e.clientY) - rect.top;
    let x = cx / rect.width;
    let y = cy / rect.height;
    if (geometry === 'circle') {
      const dx = x - 0.5, dy = y - 0.5;
      const d = Math.sqrt(dx*dx + dy*dy);
      if (d > 0.5) { x = 0.5 + dx / d * 0.5; y = 0.5 + dy / d * 0.5; }
    }
    x = Math.max(0, Math.min(1, x));
    y = Math.max(0, Math.min(1, y));
    setPos({ x, y });
  };

  useEffect(() => {
    if (!drag) return;
    const move = (e) => moveDrag(e);
    const up = () => setDrag(false);
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
    window.addEventListener('touchmove', move);
    window.addEventListener('touchend', up);
    return () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
      window.removeEventListener('touchmove', move);
      window.removeEventListener('touchend', up);
    };
  }, [drag]);

  const radius = geometry === 'square' ? 16 : geometry === 'circle' ? size/2 : 0;

  return (
    <div style={{ width: size, position: 'relative', userSelect: 'none', padding: '24px 56px' }}>
      {/* External axis labels */}
      <div className="meta" style={{ position: 'absolute', top: 4, left: '50%', transform: 'translateX(-50%)', color: 'var(--ink-2)', whiteSpace: 'nowrap' }}>↑ HIGH ENERGY · 高能量</div>
      <div className="meta" style={{ position: 'absolute', bottom: 4, left: '50%', transform: 'translateX(-50%)', color: 'var(--ink-2)', whiteSpace: 'nowrap' }}>↓ LOW · 低能量</div>
      <div className="meta" style={{ position: 'absolute', top: '50%', left: 0, transform: 'translateY(-50%) rotate(-90deg)', transformOrigin: 'center', color: 'var(--ink-2)', whiteSpace: 'nowrap' }}>← NEGATIVE · 负向</div>
      <div className="meta" style={{ position: 'absolute', top: '50%', right: 0, transform: 'translateY(-50%) rotate(90deg)', transformOrigin: 'center', color: 'var(--ink-2)', whiteSpace: 'nowrap' }}>POSITIVE · 正向 →</div>

      <div
        ref={ref}
        onMouseDown={startDrag}
        onTouchStart={startDrag}
        style={{
          width: size, height: size,
          borderRadius: radius,
          position: 'relative',
          cursor: drag ? 'grabbing' : 'grab',
          overflow: geometry === 'blob' ? 'visible' : 'hidden',
          boxShadow: 'inset 0 0 0 1px var(--rule)',
        }}
      >
        {geometry === 'blob' ? (
          <MoodBlob size={size} drift={!drag} geometry="blob" />
        ) : (
          <div style={{
            position: 'absolute', inset: 0, borderRadius: radius,
            background: `
              radial-gradient(circle at 0% 0%, var(--mood-a) 0%, transparent 55%),
              radial-gradient(circle at 100% 0%, var(--mood-b) 0%, transparent 55%),
              radial-gradient(circle at 100% 100%, var(--mood-c) 0%, transparent 60%),
              radial-gradient(circle at 0% 100%, var(--mood-d) 0%, transparent 60%)`,
          }}/>
        )}

        {/* Knob */}
        <div style={{
          position: 'absolute',
          left: `${pos.x * 100}%`, top: `${pos.y * 100}%`,
          transform: 'translate(-50%,-50%)',
          width: 24, height: 24, borderRadius: '50%',
          background: 'var(--paper)',
          boxShadow: '0 2px 14px rgba(0,0,0,0.35), 0 0 0 1px var(--ink)',
        }}>
          <div style={{ position: 'absolute', inset: -6, borderRadius: '50%', border: '1px solid var(--paper)', opacity: drag ? 0.8 : 0.3 }}/>
        </div>
      </div>
    </div>
  );
}

/* ---------- Mini icons (line) ---------- */
const Icon = {
  play: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M7 5l12 7-12 7V5z"/></svg>),
  pause: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg>),
  prev: (p={}) => (<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M6 5h2v14H6zM21 5l-12 7 12 7V5z"/></svg>),
  next: (p={}) => (<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M16 5h2v14h-2zM3 5l12 7-12 7V5z"/></svg>),
  heart: (p={}) => (<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M12 21s-7-4.5-9.5-9.5C.5 7.5 3 4 6.5 4c2 0 3.5 1 5.5 3 2-2 3.5-3 5.5-3C21 4 23.5 7.5 21.5 11.5 19 16.5 12 21 12 21z"/></svg>),
  skip: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M5 12h14M13 6l6 6-6 6"/></svg>),
  share: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M4 12v7h16v-7M12 3v13M7 8l5-5 5 5"/></svg>),
  more: (p={}) => (<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" {...p}><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>),
  arrow: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M5 12h14M13 6l6 6-6 6"/></svg>),
  chevL: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M15 6l-6 6 6 6"/></svg>),
  search: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><circle cx="11" cy="11" r="6"/><path d="M20 20l-4-4"/></svg>),
  bell: (p={}) => (<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" {...p}><path d="M6 16V11a6 6 0 1112 0v5l1.5 2H4.5L6 16z"/><path d="M10 21h4"/></svg>),
};

/* ---------- Generic page chrome ---------- */
function ScreenFrame({ children, theme = 'light', mood = 'dusk', width, height, scroll = false, label, overlay }) {
  return (
    <div
      data-theme={theme}
      data-mood={mood}
      className="mfm"
      data-screen-label={label}
      style={{
        width, height,
        background: 'var(--bg)',
        overflow: 'hidden',
        position: 'relative',
      }}
    >
      <div style={{ position:'absolute', inset:0, overflow: scroll ? 'auto' : 'hidden' }}>
        {children}
      </div>
      {overlay}
    </div>
  );
}

Object.assign(window, { MoodBlob, MoodWheel, Icon, ScreenFrame });
