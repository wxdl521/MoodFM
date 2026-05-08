import { useNavigate } from 'react-router-dom';

export default function History() {
  const navigate = useNavigate();
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 24, opacity: 0.4 }}>历史记录</div>
      <div style={{ fontFamily: 'var(--mono)', fontSize: 12, opacity: 0.3, letterSpacing: '.1em' }}>COMING SOON</div>
      <button onClick={() => navigate(-1)} style={{ marginTop: 16, padding: '8px 20px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 20, cursor: 'pointer' }}>
        返回
      </button>
    </div>
  );
}
