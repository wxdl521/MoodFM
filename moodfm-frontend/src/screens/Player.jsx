import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoodBlob, Icon } from '../components/atoms';
import { usePlayerStore } from '../store/playerStore';
import { useRadioStore } from '../store/radioStore';
import { useAudioPlayer } from '../hooks/useAudioPlayer';

function ChipDark({ children, active, onClick }) {
  return (
    <button onClick={onClick} style={{
      height: 34, padding: '0 14px', borderRadius: 999,
      border: '1px solid rgba(255,255,255,0.22)',
      background: active ? 'rgba(255,255,255,0.12)' : 'transparent',
      color: '#fff', cursor: 'pointer',
      fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.12em', textTransform: 'uppercase',
      display: 'inline-flex', alignItems: 'center', gap: 6,
    }}>{children}</button>
  );
}

const ctrlBtn = {
  width: 48, height: 48, borderRadius: '50%',
  background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.2)',
  color: '#fff', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
};

function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export default function Player() {
  const navigate = useNavigate();
  const { session, queue, currentIndex, mood } = usePlayerStore();
  const { lastSkippedArtist } = useRadioStore();

  const song     = queue[currentIndex];
  const upcoming = queue.slice(currentIndex + 1, currentIndex + 6);

  const [liked, setLiked]           = useState(false);
  const [showBanner, setShowBanner] = useState(false);

  const { play, pause, seek, skip, like, dislike, prev, playing, progress, duration, loadingUrl } = useAudioPlayer();

  const handleSkip = () => {
    const shouldPrompt = skip();
    if (shouldPrompt) setShowBanner(true);
  };

  const handleLike = () => {
    setLiked(x => !x);
    like();
  };

  const togglePlay = () => { playing ? pause() : play(); };

  const handleSeek = (e) => {
    const rect  = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    seek(ratio);
  };

  if (!song) {
    return (
      <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 20 }}>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, color: 'var(--ink-2)' }}>还没有电台</div>
        <button className="btn" onClick={() => navigate('/home')}>去选择心情 <Icon.arrow /></button>
      </div>
    );
  }

  const currentSeconds = progress * duration;

  return (
    <div data-mood={mood} className="mfm player-root">
      <div className="player-backdrop" style={{
        background: `radial-gradient(circle at 20% 25%, var(--mood-a) 0%, transparent 55%),
                     radial-gradient(circle at 80% 20%, var(--mood-b) 0%, transparent 55%),
                     radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
                     radial-gradient(circle at 10% 80%, var(--mood-d) 0%, transparent 65%)`,
        backgroundColor: 'var(--mood-d)', filter: 'saturate(1.1)',
      }}/>
      <div className="player-backdrop" style={{ background: 'rgba(0,0,0,0.18)' }}/>

      <div className="player-content">
        {/* Top bar */}
        <div className="player-topbar">
          <button onClick={() => navigate('/home')} className="row" style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer', gap: 8 }}>
            <Icon.chevL/> <span className="mono" style={{ fontSize: 11, letterSpacing: '.16em' }}>BACK</span>
          </button>
          <div style={{ textAlign: 'center' }}>
            <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: 0.7 }}>
              TRACK {currentIndex + 1} / {queue.length} · NOW PLAYING
            </div>
            <div className="player-station-title">
              {session?.moodSummary || 'Your Radio'}
            </div>
          </div>
          <button className="row" style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer' }}>
            <Icon.more/>
          </button>
        </div>

        {/* Main content */}
        <div className="player-main">
          <div style={{ display: 'flex', justifyContent: 'center', position: 'relative' }}>
            <div className="player-pulse-ring"/>
            {song.coverUrl ? (
              <img src={song.coverUrl} alt="cover" className="player-cover-img"/>
            ) : (
              <MoodBlob className="player-cover-blob" drift={playing} geometry="circle"/>
            )}
          </div>

          <div>
            <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: 0.7 }}>
              {song.platform || 'NETEASE'} {loadingUrl ? '· LOADING…' : ''}
            </div>
            <h1 className="display player-track-title">
              {song.title}<em style={{ opacity: 0.5 }}>.</em>
            </h1>
            <div className="player-artist" style={{ fontFamily: 'var(--serif-cn)' }}>
              {song.artist}
              {song.album && <span style={{ opacity: 0.6, fontSize: 16 }}> · {song.album}</span>}
            </div>

            {song.recommendReason && (
              <div style={{ marginTop: 20, padding: 16, background: 'rgba(255,255,255,0.08)', backdropFilter: 'blur(20px)', borderRadius: 16, border: '1px solid rgba(255,255,255,0.14)' }}>
                <div className="mono" style={{ fontSize: 10, letterSpacing: '.18em', opacity: 0.7 }}>AI · WHY THIS SONG</div>
                <div style={{ marginTop: 8, fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.65, opacity: 0.95 }}>
                  {song.recommendReason}
                </div>
              </div>
            )}

            {/* Progress */}
            <div style={{ marginTop: 24 }}>
              <div onClick={handleSeek} style={{ height: 4, background: 'rgba(255,255,255,0.2)', borderRadius: 2, position: 'relative', cursor: 'pointer' }}>
                <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${progress * 100}%`, background: '#fff', borderRadius: 2 }}/>
                <div style={{ position: 'absolute', left: `${progress * 100}%`, top: -4, width: 12, height: 12, borderRadius: '50%', background: '#fff', transform: 'translateX(-50%)', boxShadow: '0 2px 8px rgba(0,0,0,0.3)' }}/>
              </div>
              <div className="mono between" style={{ fontSize: 11, marginTop: 8, opacity: 0.7 }}>
                <span>{formatTime(currentSeconds)}</span>
                <span>{formatTime(duration)}</span>
              </div>
            </div>

            {/* Controls */}
            <div className="row player-controls">
              <button style={ctrlBtn} onClick={prev}><Icon.prev/></button>
              <button onClick={togglePlay} disabled={loadingUrl}
                style={{ ...ctrlBtn, width: 64, height: 64, background: '#fff', color: 'var(--ink)', opacity: loadingUrl ? 0.6 : 1 }}>
                {playing ? <Icon.pause/> : <Icon.play/>}
              </button>
              <button style={ctrlBtn} onClick={() => { dislike(); }}><Icon.next/></button>
              <button style={{ ...ctrlBtn, marginLeft: 12 }} onClick={handleSkip}>
                <Icon.skip/>
              </button>
            </div>

            {/* Chips */}
            <div className="row" style={{ marginTop: 20, gap: 8, flexWrap: 'wrap' }}>
              <ChipDark active={liked} onClick={handleLike}>
                <Icon.heart style={{ fill: liked ? 'var(--mood-a)' : 'none', stroke: liked ? 'var(--mood-a)' : 'currentColor' }}/>
                {liked ? '已红心' : '红心'}
              </ChipDark>
              <ChipDark onClick={() => { dislike(); }}>不喜欢</ChipDark>
              <ChipDark>队列 · {queue.length - currentIndex - 1}</ChipDark>
              <ChipDark><Icon.share/> 分享</ChipDark>
            </div>
          </div>
        </div>

        {/* Queue strip */}
        <div className="player-queue">
          <div className="mono" style={{ fontSize: 10, letterSpacing: '.18em', opacity: 0.6, whiteSpace: 'nowrap' }}>
            {upcoming.length > 0 ? 'UP NEXT →' : 'QUEUE'}
          </div>
          {upcoming.map((s, i) => (
            <div key={i} className="row" style={{ gap: 8, padding: '6px 12px', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 999, whiteSpace: 'nowrap', flexShrink: 0 }}>
              <span className="mono" style={{ fontSize: 10, opacity: 0.6 }}>0{currentIndex + i + 2}</span>
              <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 13 }}>{s.title}</span>
              <span style={{ fontSize: 12, opacity: 0.6 }}>· {s.artist}</span>
            </div>
          ))}
          {session?.playlistId && (
            <button onClick={() => navigate(`/playlist/${session.playlistId}`)}
              style={{ marginLeft: 'auto', flexShrink: 0, background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)', color: '#fff', borderRadius: 999, padding: '6px 14px', cursor: 'pointer', fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '.14em', whiteSpace: 'nowrap' }}>
              歌单详情 →
            </button>
          )}
        </div>

        {/* Blacklist banner */}
        {showBanner && (
          <div style={{
            position: 'fixed', bottom: 100, left: '50%', transform: 'translateX(-50%)',
            background: 'var(--ink)', color: 'var(--bg)', borderRadius: 24,
            padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 14, zIndex: 50,
            maxWidth: 340, width: 'calc(100% - 40px)',
          }}>
            <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
              连续跳过了 3 首，要屏蔽「{lastSkippedArtist}」吗？
            </div>
            <button onClick={() => setShowBanner(false)} style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', opacity: 0.6, fontSize: 18 }}>✕</button>
          </div>
        )}
      </div>
    </div>
  );
}
