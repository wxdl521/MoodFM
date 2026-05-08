import { useNavigate } from 'react-router-dom';

export default function ErrorPage() {
  const navigate = useNavigate();
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
      <div style={{ fontFamily: 'var(--mono)', fontSize: 72, opacity: 0.2, letterSpacing: '.05em' }}>404</div>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 24, opacity: 0.4 }}>页面不存在</div>
      <button onClick={() => navigate('/home')} style={{ marginTop: 16, padding: '8px 20px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 20, cursor: 'pointer' }}>
        回到首页
      </button>
    </div>
  );
}
