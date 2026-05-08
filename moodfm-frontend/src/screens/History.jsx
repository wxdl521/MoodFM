import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { historyApi } from '../api/history';

const SCENES = ['全部', '通勤', '学习', '跑步', '写作', '睡前', '派对'];

const ACTION_LABEL = {
  completed: '✓ 听完',
  liked:     '♥ 喜欢',
  skip_early:'→ 跳过',
  skip_late: '→ 跳过',
};

function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  return `${mm}-${dd} ${hh}:${mi}`;
}

export default function History() {
  const navigate = useNavigate();
  const [records, setRecords] = useState([]);
  const [scene, setScene]     = useState('全部');
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    historyApi.list({ scene: scene === '全部' ? undefined : scene, pageSize: 50 })
      .then(d => {
        const rows = Array.isArray(d) ? d : (d?.records ?? d?.list ?? []);
        setRecords(rows);
        setLoading(false);
      })
      .catch(() => { setError('加载失败，请稍后重试'); setLoading(false); });
  }, [scene]);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)' }}>
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', borderBottom: '1px solid var(--rule)',
        padding: '14px 20px', display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center' }}>
          <Icon.chevL/>
        </button>
        <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, margin: 0 }}>历史记录</h1>
      </div>

      <div style={{ padding: '24px 20px 80px', maxWidth: 720, margin: '0 auto' }}>
        <div className="meta" style={{ marginBottom: 6 }}>PLAY HISTORY · 播放历史</div>
        <h2 className="display" style={{ fontSize: 52, margin: '6px 0 20px', lineHeight: 1 }}>History.</h2>

        {/* Scene filter chips */}
        <div style={{ display: 'flex', gap: 8, overflowX: 'auto', paddingBottom: 4, marginBottom: 24 }}>
          {SCENES.map(s => (
            <button key={s} onClick={() => setScene(s)} style={{
              flexShrink: 0, padding: '6px 16px', borderRadius: 999,
              border: '1px solid var(--rule)',
              background: scene === s ? 'var(--ink)' : 'transparent',
              color: scene === s ? 'var(--bg)' : 'var(--ink)',
              cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13,
              transition: 'background .15s',
            }}>{s}</button>
          ))}
        </div>

        {loading && <div className="mono" style={{ opacity: 0.5 }}>加载中…</div>}
        {error   && <div style={{ color: 'var(--mood-b, #e05)', fontFamily: 'var(--serif-cn)' }}>{error}</div>}

        {!loading && !error && records.length === 0 && (
          <div style={{ textAlign: 'center', paddingTop: 60, opacity: 0.4, fontFamily: 'var(--serif-cn)' }}>还没有播放记录</div>
        )}

        {!loading && !error && records.map(r => (
          <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)' }}>
            <div style={{ width: 44, height: 44, borderRadius: 6, background: 'var(--paper)', overflow: 'hidden', flexShrink: 0, border: '1px solid var(--rule)' }}>
              {r.song?.coverUrl
                ? <img src={r.song.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2 }}>♪</div>
              }
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {r.song?.title ?? r.title ?? '—'}
              </div>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>
                {r.song?.artist ?? r.artist ?? ''}
              </div>
            </div>
            <div style={{ textAlign: 'right', flexShrink: 0 }}>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>{fmtDate(r.playedAt)}</div>
              <div style={{ fontSize: 11, marginTop: 2, opacity: 0.6 }}>
                {ACTION_LABEL[r.action] ?? r.action ?? ''}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
