import { useNavigate } from 'react-router-dom';

export default function ErrorPage() {
  const navigate = useNavigate();
  return (
    <div style={{
      minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16,
    }}>
      <div className="mono" style={{ fontSize: 80, opacity: 0.12, letterSpacing: '.08em', lineHeight: 1 }}>404</div>
      <h1 className="display" style={{ fontSize: 48, margin: '0 0 4px', textAlign: 'center' }}>Lost.</h1>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, opacity: 0.5 }}>这首歌找不到了</div>
      <button
        onClick={() => navigate('/home')}
        className="btn"
        style={{ marginTop: 8, padding: '12px 28px' }}
      >
        回到首页
      </button>
    </div>
  );
}
