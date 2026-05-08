import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { playlistApi } from '../api/playlist';

export default function PlaylistList() {
  const navigate = useNavigate();
  const [playlists, setPlaylists] = useState([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState(null);

  useEffect(() => {
    playlistApi.list()
      .then(d => { setPlaylists(Array.isArray(d) ? d : []); setLoading(false); })
      .catch(() => { setError('加载失败，请稍后重试'); setLoading(false); });
  }, []);

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
        <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, margin: 0, flex: 1 }}>我的歌单</h1>
        <span className="mono" style={{ fontSize: 11, opacity: 0.45 }}>{playlists.length} 个</span>
      </div>

      <div style={{ padding: '24px 20px 80px', maxWidth: 720, margin: '0 auto' }}>
        <div className="meta" style={{ marginBottom: 6 }}>MY PLAYLISTS · 歌单</div>
        <h2 className="display" style={{ fontSize: 52, margin: '6px 0 28px', lineHeight: 1 }}>Playlists.</h2>

        {loading && <div className="mono" style={{ opacity: 0.5 }}>加载中…</div>}
        {error   && <div style={{ color: 'var(--mood-b, #e05)', fontFamily: 'var(--serif-cn)' }}>{error}</div>}

        {!loading && !error && playlists.length === 0 && (
          <div style={{ textAlign: 'center', paddingTop: 60, opacity: 0.4, fontFamily: 'var(--serif-cn)' }}>还没有歌单</div>
        )}

        {!loading && !error && playlists.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 20 }}>
            {playlists.map(pl => (
              <div key={pl.id} onClick={() => navigate(`/playlist/${pl.id}`)} style={{ cursor: 'pointer' }}>
                <div style={{
                  aspectRatio: '1', background: 'var(--paper)', borderRadius: 12,
                  overflow: 'hidden', marginBottom: 10, border: '1px solid var(--rule)',
                }}>
                  {pl.coverUrl
                    ? <img src={pl.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.2, fontSize: 32 }}>♪</div>
                  }
                </div>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.4, marginBottom: 2 }}>{pl.name}</div>
                <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>{pl.trackCount ?? 0} 首</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
