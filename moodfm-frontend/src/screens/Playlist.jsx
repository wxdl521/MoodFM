import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { MoodBlob, Icon, Logo } from '../components/atoms';
import { playlistApi } from '../api/playlist';
import { usePlayerStore } from '../store/playerStore';

function formatDuration(minutes) {
  if (!minutes) return '—';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}H ${m}M` : `${m} MIN`;
}

function TrackRow({ track, index, active, onPlay, onLikeToggle }) {
  const [hover, setHover] = useState(false);
  const [liked, setLiked] = useState(track.liked ?? false);

  const toggleLike = (e) => {
    e.stopPropagation();
    setLiked(l => !l);
    onLikeToggle?.(track.id, !liked);
  };

  return (
    <div
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onClick={() => onPlay(index)}
      className="row"
      style={{
        padding: '12px 16px', borderRadius: 8, cursor: 'pointer',
        background: active ? 'var(--bg-2)' : hover ? 'var(--bg-2)' : 'transparent',
        transition: 'background .15s',
      }}
    >
      {/* # */}
      <div style={{ width: 32, fontFamily: 'var(--mono)', fontSize: 13, color: active ? 'var(--ink)' : 'var(--ink-3)', flexShrink: 0 }}>
        {active ? '▸' : hover ? <Icon.play /> : String(index + 1).padStart(2, '0')}
      </div>

      {/* Title + Artist */}
      <div style={{ flex: 2.5, minWidth: 0 }}>
        <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 18, color: 'var(--ink)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {track.title}
        </div>
        <div className="meta" style={{ marginTop: 2 }}>{track.artist}</div>
      </div>

      {/* Album */}
      <div style={{ flex: 1.5, minWidth: 0, fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--ink-2)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
        {track.album || '—'}
      </div>

      {/* Mood tag */}
      <div style={{ width: 100, flexShrink: 0 }}>
        {track.mood && (
          <span className="meta" style={{ padding: '3px 8px', border: '1px solid var(--rule)', borderRadius: 999, color: 'var(--ink-2)' }}>
            {track.mood}
          </span>
        )}
      </div>

      {/* Like */}
      <div style={{ width: 40, textAlign: 'center', flexShrink: 0 }}>
        <button onClick={toggleLike} style={{ background: 'none', border: 'none', color: liked ? 'var(--mood-b)' : 'var(--ink-3)', cursor: 'pointer', fontSize: 18, lineHeight: 1 }}>
          {liked ? '♥' : '♡'}
        </button>
      </div>

      {/* Duration */}
      <div style={{ width: 60, textAlign: 'right', fontFamily: 'var(--mono)', fontSize: 12, color: 'var(--ink-3)', flexShrink: 0 }}>
        {track.durationFormatted || '—'}
      </div>
    </div>
  );
}

function TrackRowMobile({ track, index, active, onPlay, onLikeToggle }) {
  const [liked, setLiked] = useState(track.liked ?? false);

  const toggleLike = (e) => {
    e.stopPropagation();
    setLiked(l => !l);
    onLikeToggle?.(track.id, !liked);
  };

  return (
    <div onClick={() => onPlay(index)} className="row" style={{ gap: 12, padding: '12px 6px', borderBottom: '1px solid var(--rule)', cursor: 'pointer' }}>
      <span className="mono" style={{ color: active ? 'var(--ink)' : 'var(--ink-3)', width: 22, fontSize: 12, flexShrink: 0 }}>
        {active ? '▸' : String(index + 1).padStart(2, '0')}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 16, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{track.title}</div>
        <div className="meta" style={{ marginTop: 2 }}>{track.artist} {track.mood ? `· ${track.mood}` : ''}</div>
      </div>
      <button onClick={toggleLike} style={{ background: 'none', border: 'none', color: liked ? 'var(--mood-b)' : 'var(--ink-3)', cursor: 'pointer', fontSize: 18, lineHeight: 1, flexShrink: 0 }}>
        {liked ? '♥' : '♡'}
      </button>
      <div className="mono" style={{ color: 'var(--ink-3)', fontSize: 12, width: 36, textAlign: 'right', flexShrink: 0 }}>
        {track.durationFormatted || '—'}
      </div>
    </div>
  );
}

export default function Playlist() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { setSession } = usePlayerStore();

  const [playlist, setPlaylist] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [playingIndex, setPlayingIndex] = useState(-1);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    setLoading(true);
    playlistApi.getPlaylist(id)
      .then(res => {
        if (res.code === 200) setPlaylist(res.data);
        else setError(res.message || '歌单加载失败');
      })
      .catch(() => setError('网络错误，请刷新重试'))
      .finally(() => setLoading(false));
  }, [id]);

  const startPlay = async (startIndex = 0) => {
    setStarting(true);
    try {
      const res = await playlistApi.playFromPlaylist(id, startIndex);
      if (res.code === 200) {
        const { sessionId, moodSummary, scene, playlistId, songs } = res.data;
        setSession({ id: sessionId, moodSummary, scene, playlistId }, songs || []);
        navigate('/player');
      }
    } catch (e) {
      console.error(e);
    } finally {
      setStarting(false);
    }
  };

  if (loading) {
    return (
      <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, color: 'var(--ink-2)' }}>加载中…</div>
      </div>
    );
  }

  if (error || !playlist) {
    return (
      <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 20 }}>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, color: 'var(--ink-2)' }}>{error || '歌单不存在'}</div>
        <button className="btn" onClick={() => navigate(-1)}><Icon.chevL /> 返回</button>
      </div>
    );
  }

  const tracks = playlist.tracks || [];
  const dominantMoods = playlist.dominantMoods || [];

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh' }}>
      {/* Top bar */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', padding: '18px 56px',
        borderBottom: '1px solid var(--rule)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <button onClick={() => navigate(-1)} className="btn-pill"><Icon.chevL /> 返回</button>
        <div className="meta">PLAYLIST · 歌单详情</div>
        <div className="row" style={{ gap: 8 }}>
          <div className="row" style={{ gap: 10 }}>
            <Logo size={20} />
            <div style={{ fontFamily: 'var(--serif-en)', fontSize: 18, fontStyle: 'italic' }}>MoodFM</div>
          </div>
        </div>
      </div>

      {/* Hero */}
      <div style={{ position: 'relative', overflow: 'hidden', padding: '56px 56px 48px', borderBottom: '1px solid var(--rule)' }}>
        <div className="mood-blob drift" style={{ width: 800, height: 800, right: -150, top: -220, opacity: 0.45 }} />

        <div style={{ position: 'relative', zIndex: 2, display: 'grid', gridTemplateColumns: '300px 1fr', gap: 48, alignItems: 'end' }}>
          {/* Cover */}
          <div>
            <div style={{ position: 'relative', width: 300, height: 300, borderRadius: 18, overflow: 'hidden', boxShadow: '0 24px 60px rgba(0,0,0,0.18)' }}>
              {playlist.coverUrl
                ? <img src={playlist.coverUrl} alt="cover" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                : <MoodBlob size={300} drift={true} geometry="blob" />
              }
              {playlist.name && (
                <div style={{ position: 'absolute', left: 14, bottom: 12, color: '#fff', textShadow: '0 1px 4px rgba(0,0,0,0.4)' }}>
                  <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: 0.9 }}>
                    {playlist.name.toUpperCase().slice(0, 20)}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Meta */}
          <div>
            <div className="meta">CURATED FOR YOU · 为你编排</div>
            <h1 className="display" style={{ fontSize: 108, margin: '12px 0 0', lineHeight: 0.95 }}>
              {playlist.name}<em style={{ opacity: 0.4 }}>.</em>
            </h1>
            {playlist.nameCn && (
              <div className="display-cn" style={{ fontSize: 32, marginTop: 4, color: 'var(--ink-2)' }}>{playlist.nameCn}</div>
            )}
            {playlist.description && (
              <p style={{ marginTop: 16, fontSize: 14, lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: 520, fontFamily: 'var(--serif-cn)' }}>
                {playlist.description}
              </p>
            )}

            {/* Stats */}
            <div className="row" style={{ marginTop: 20, gap: 24, color: 'var(--ink-3)' }}>
              <div>
                <div className="meta">TRACKS</div>
                <div className="mono" style={{ fontSize: 14, color: 'var(--ink)', marginTop: 2 }}>{playlist.trackCount ?? tracks.length}</div>
              </div>
              {playlist.totalDurationMinutes && (
                <div>
                  <div className="meta">DURATION</div>
                  <div className="mono" style={{ fontSize: 14, color: 'var(--ink)', marginTop: 2 }}>{formatDuration(playlist.totalDurationMinutes)}</div>
                </div>
              )}
              {playlist.updatedAt && (
                <div>
                  <div className="meta">UPDATED</div>
                  <div className="mono" style={{ fontSize: 14, color: 'var(--ink)', marginTop: 2 }}>
                    {new Date(playlist.updatedAt).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })}
                  </div>
                </div>
              )}
              {dominantMoods.length > 0 && (
                <div>
                  <div className="meta">DOMINANT</div>
                  <div className="mono" style={{ fontSize: 14, color: 'var(--ink)', marginTop: 2 }}>{dominantMoods.join(' · ')}</div>
                </div>
              )}
              {playlist.platform && (
                <div>
                  <div className="meta">SOURCE</div>
                  <div className="mono" style={{ fontSize: 14, color: 'var(--ink)', marginTop: 2 }}>{playlist.platform.toUpperCase()}</div>
                </div>
              )}
            </div>

            {/* Actions */}
            <div className="row" style={{ marginTop: 24, gap: 10, flexWrap: 'wrap' }}>
              <button className="btn" onClick={() => startPlay(0)} disabled={starting} style={{ height: 48, padding: '0 22px', opacity: starting ? 0.7 : 1 }}>
                <Icon.play /> {starting ? '加载中…' : '播放 · PLAY ALL'}
              </button>
              <button className="btn-pill" style={{ height: 48 }}>♡ 收藏</button>
              <button className="btn-pill" style={{ height: 48 }}><Icon.share /> 分享</button>
            </div>
          </div>
        </div>
      </div>

      {/* Track list */}
      <div style={{ padding: '40px 56px 80px' }}>
        <div className="between" style={{ marginBottom: 16 }}>
          <div className="meta">TRACK LIST · 曲目</div>
          <div className="meta" style={{ color: 'var(--ink-3)' }}>{tracks.length} 首</div>
        </div>

        {/* Header row */}
        <div className="row" style={{ padding: '10px 16px', color: 'var(--ink-3)', borderBottom: '1px solid var(--rule)', marginBottom: 4 }}>
          <div className="meta" style={{ width: 32 }}>#</div>
          <div className="meta" style={{ flex: 2.5 }}>TITLE · 标题</div>
          <div className="meta" style={{ flex: 1.5 }}>ALBUM · 专辑</div>
          <div className="meta" style={{ width: 100 }}>MOOD</div>
          <div className="meta" style={{ width: 40 }}></div>
          <div className="meta" style={{ width: 60, textAlign: 'right' }}>TIME</div>
        </div>

        {tracks.length === 0 ? (
          <div style={{ padding: '40px 16px', textAlign: 'center', fontFamily: 'var(--serif-cn)', fontSize: 15, color: 'var(--ink-3)' }}>
            暂无曲目
          </div>
        ) : (
          tracks.map((track, i) => (
            <TrackRow
              key={track.id ?? i}
              track={track}
              index={i}
              active={playingIndex === i}
              onPlay={(idx) => startPlay(idx)}
              onLikeToggle={() => {}}
            />
          ))
        )}
      </div>
    </div>
  );
}
