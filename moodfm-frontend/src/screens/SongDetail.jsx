import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { songApi } from '../api/song';
import { playlistApi } from '../api/playlist';

function fmtDuration(seconds) {
  if (!seconds) return '';
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

export default function SongDetail() {
  const { id }    = useParams();
  const navigate  = useNavigate();

  const [song, setSong]       = useState(null);
  const [lyrics, setLyrics]   = useState([]);
  const [similar, setSimilar] = useState([]);
  const [liked, setLiked]     = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([songApi.get(id), songApi.lyrics(id), songApi.similar(id)])
      .then(([s, l, sim]) => {
        setSong(s);
        setLyrics(Array.isArray(l) ? l : []);
        setSimilar(Array.isArray(sim) ? sim : []);
        setLiked(!!s?.liked);
        setLoading(false);
      })
      .catch(() => { setError('加载失败'); setLoading(false); });
  }, [id]);

  const toggleLike = async () => {
    await playlistApi.toggleLike(id).catch(() => {});
    setLiked(l => !l);
  };

  if (loading) return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="mono" style={{ opacity: 0.5 }}>加载中…</div>
    </div>
  );

  if (error) return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
      <div style={{ fontFamily: 'var(--serif-cn)', opacity: 0.6 }}>{error}</div>
      <button onClick={() => navigate(-1)} className="btn-pill">返回</button>
    </div>
  );

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', paddingBottom: 80 }}>
      {/* Sticky top bar */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', borderBottom: '1px solid var(--rule)',
        padding: '14px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center', gap: 6 }}>
          <Icon.chevL/> <span className="mono" style={{ fontSize: 11, letterSpacing: '.12em' }}>BACK</span>
        </button>
        <div className="mono" style={{ fontSize: 10, letterSpacing: '.18em', opacity: 0.5 }}>SONG · 单曲</div>
        <div style={{ width: 60 }}/>
      </div>

      <div style={{ maxWidth: 640, margin: '0 auto', padding: '32px 20px' }}>
        {/* Cover + meta */}
        <div style={{ display: 'flex', gap: 24, marginBottom: 36, alignItems: 'flex-start' }}>
          <div style={{ width: 140, height: 140, borderRadius: 12, overflow: 'hidden', flexShrink: 0, background: 'var(--paper)', border: '1px solid var(--rule)' }}>
            {song?.coverUrl
              ? <img src={song.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0.15, fontSize: 48 }}>♪</div>
            }
          </div>
          <div style={{ flex: 1, minWidth: 0, paddingTop: 4 }}>
            <div className="meta" style={{ marginBottom: 8 }}>{song?.platform ?? 'NETEASE'}</div>
            <h2 style={{ fontFamily: 'var(--serif-cn)', fontSize: 22, margin: '0 0 8px', lineHeight: 1.3 }}>{song?.title}</h2>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, opacity: 0.6, marginBottom: 4 }}>{song?.artist}</div>
            {song?.album && <div className="mono" style={{ fontSize: 11, opacity: 0.4, marginBottom: 16 }}>{song.album}</div>}
            {song?.durationSeconds && (
              <div className="mono" style={{ fontSize: 11, opacity: 0.4, marginBottom: 16 }}>{fmtDuration(song.durationSeconds)}</div>
            )}
            <button onClick={toggleLike} style={{
              background: 'none', border: '1px solid var(--rule)', borderRadius: 999,
              padding: '6px 16px', cursor: 'pointer', color: liked ? 'var(--mood-a, #e05)' : 'var(--ink)',
              fontFamily: 'var(--serif-cn)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6,
            }}>
              <span style={{ fontSize: 16 }}>{liked ? '♥' : '♡'}</span>
              {liked ? '已红心' : '红心'}
            </button>
          </div>
        </div>

        {/* AI recommendation reason */}
        {song?.recommendReason && (
          <div style={{ marginBottom: 32, padding: '16px 20px', background: 'var(--paper)', border: '1px solid var(--rule)', borderRadius: 14 }}>
            <div className="meta" style={{ marginBottom: 8 }}>AI · WHY THIS SONG</div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.7, opacity: 0.85 }}>
              {song.recommendReason}
            </div>
          </div>
        )}

        {/* Lyrics */}
        {lyrics.length > 0 && (
          <div style={{ marginBottom: 36 }}>
            <div className="meta" style={{ marginBottom: 14 }}>LYRICS · 歌词</div>
            {lyrics.map((line, i) => (
              <div key={i} style={{ padding: '4px 0', fontFamily: 'var(--serif-cn)', fontSize: 15, lineHeight: 1.9, opacity: 0.8 }}>
                {line.text ?? line}
              </div>
            ))}
          </div>
        )}

        {/* Similar tracks */}
        {similar.length > 0 && (
          <div>
            <div className="meta" style={{ marginBottom: 14 }}>SIMILAR · 相似歌曲</div>
            {similar.map(s => (
              <div key={s.id} onClick={() => navigate(`/song/${s.id}`)} style={{
                display: 'flex', alignItems: 'center', gap: 14,
                padding: '10px 0', borderBottom: '1px solid var(--rule)', cursor: 'pointer',
              }}>
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
                <span style={{ fontSize: 16, opacity: 0.3 }}>›</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
