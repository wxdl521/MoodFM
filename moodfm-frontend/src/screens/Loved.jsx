import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { playlistApi } from '../api/playlist';

function fmtDuration(seconds) {
  if (!seconds) return '';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

export default function Loved() {
  const navigate = useNavigate();
  const [songs, setSongs]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    playlistApi.loved()
      .then(d => { setSongs(Array.isArray(d) ? d : []); setLoading(false); })
      .catch(() => { setError('加载失败，请稍后重试'); setLoading(false); });
  }, []);

  const handleUnlike = async (e, songId) => {
    e.stopPropagation();
    await playlistApi.toggleLike(songId).catch(() => {});
    setSongs(prev => prev.filter(s => s.id !== songId));
  };

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
        <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, margin: 0, flex: 1 }}>我喜欢的音乐</h1>
        <span className="mono" style={{ fontSize: 11, opacity: 0.45 }}>{songs.length} 首</span>
      </div>

      <div style={{ padding: '24px 20px 80px', maxWidth: 720, margin: '0 auto' }}>
        <div className="meta" style={{ marginBottom: 6 }}>LIKED SONGS · 红心歌曲</div>
        <h2 className="display" style={{ fontSize: 52, margin: '6px 0 28px', lineHeight: 1 }}>Loved.</h2>

        {loading && <div className="mono" style={{ opacity: 0.5 }}>加载中…</div>}
        {error   && <div style={{ color: 'var(--mood-b, #e05)', fontFamily: 'var(--serif-cn)' }}>{error}</div>}

        {!loading && !error && songs.length === 0 && (
          <div style={{ textAlign: 'center', paddingTop: 60, opacity: 0.4, fontFamily: 'var(--serif-cn)' }}>
            还没有红心歌曲 · 在播放器中点击红心收藏
          </div>
        )}

        {!loading && !error && songs.map((s, i) => (
          <div key={s.id}
            onClick={() => navigate(`/song/${s.id}`)}
            style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)', cursor: 'pointer' }}
          >
            <span className="mono" style={{ width: 24, textAlign: 'right', opacity: 0.35, fontSize: 12, flexShrink: 0 }}>{i + 1}</span>
            <div style={{ width: 44, height: 44, borderRadius: 6, background: 'var(--paper)', overflow: 'hidden', flexShrink: 0, border: '1px solid var(--rule)' }}>
              {s.coverUrl
                ? <img src={s.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2 }}>♪</div>
              }
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.title}</div>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{s.artist}</div>
            </div>
            <div className="mono" style={{ fontSize: 12, opacity: 0.4, flexShrink: 0 }}>{fmtDuration(s.durationSeconds)}</div>
            <button
              onClick={(e) => handleUnlike(e, s.id)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--mood-a, #e05)', fontSize: 16, flexShrink: 0 }}
              title="取消红心"
            >♥</button>
          </div>
        ))}
      </div>
    </div>
  );
}
