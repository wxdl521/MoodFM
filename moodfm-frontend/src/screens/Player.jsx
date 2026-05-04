import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Howl } from 'howler';
import { MoodBlob, Icon } from '../components/atoms';
import { radioApi } from '../api/radio';
import { usePlayerStore } from '../store/playerStore';

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
  const {
    session, queue, currentIndex, playing, mood,
    setPlaying, setProgress, next, prev,
  } = usePlayerStore();

  const song = queue[currentIndex];
  const upcoming = queue.slice(currentIndex + 1, currentIndex + 6);

  const [liked, setLiked] = useState(false);
  const [progress, setLocalProgress] = useState(0);   // 0–1
  const [duration, setDuration] = useState(0);
  const [loadingUrl, setLoadingUrl] = useState(false);
  const howlRef = useRef(null);
  const rafRef = useRef(null);
  const loadIdRef = useRef(0);  // 版本号：过期的异步回调直接放弃

  // ---- 获取播放地址并创建 Howl ----
  const loadSong = useCallback(async (s) => {
    if (!s) return;

    // 每次调用递增版本号，旧的异步回调持有旧版本号会被忽略
    const myId = ++loadIdRef.current;

    // 先把旧 Howl 的回调全部摘掉再 stop/unload，防止 onstop 触发多余的 setPlaying
    if (howlRef.current) {
      howlRef.current.off();
      howlRef.current.stop();
      howlRef.current.unload();
      howlRef.current = null;
    }
    cancelAnimationFrame(rafRef.current);
    setLocalProgress(0);
    setDuration(0);
    setLoadingUrl(true);

    try {
      const res = await radioApi.getSongUrl(s.platform, s.platformSongId);

      // 如果在等待期间有更新的加载请求发出，直接放弃
      if (myId !== loadIdRef.current) return;

      const url = typeof res === 'string' ? res : res?.data;
      if (!url) throw new Error('empty url');

      const howl = new Howl({
        src: [url],
        html5: true,
        onload: () => {
          if (myId !== loadIdRef.current) return;
          setDuration(howl.duration());
          setLoadingUrl(false);
        },
        onloaderror: () => {
          if (myId !== loadIdRef.current) return;
          setLoadingUrl(false);
          console.warn('Howl load error for', s.title);
        },
        onplay: () => {
          if (myId !== loadIdRef.current) return;
          setPlaying(true);
          const tick = () => {
            if (myId !== loadIdRef.current) { cancelAnimationFrame(rafRef.current); return; }
            setLocalProgress(howl.seek() / (howl.duration() || 1));
            rafRef.current = requestAnimationFrame(tick);
          };
          rafRef.current = requestAnimationFrame(tick);
        },
        onpause: () => {
          if (myId !== loadIdRef.current) return;
          setPlaying(false);
          cancelAnimationFrame(rafRef.current);
        },
        onstop: () => {
          if (myId !== loadIdRef.current) return;
          setPlaying(false);
          cancelAnimationFrame(rafRef.current);
        },
        onend: () => {
          if (myId !== loadIdRef.current) return;
          cancelAnimationFrame(rafRef.current);
          next();
        },
      });
      howlRef.current = howl;
      howl.play();
    } catch (e) {
      if (myId === loadIdRef.current) {
        console.error('loadSong error', e);
        setLoadingUrl(false);
      }
    }
  }, [next, setPlaying]);

  // 当 currentIndex / song 变化时重新加载
  useEffect(() => {
    if (song) loadSong(song);
    return () => cancelAnimationFrame(rafRef.current);
  }, [currentIndex]);

  // 卸载时停止（递增版本号使所有飞行中的回调失效）
  useEffect(() => {
    return () => {
      loadIdRef.current++;           // 作废所有飞行中的异步回调
      howlRef.current?.off();
      howlRef.current?.stop();
      howlRef.current?.unload();
      cancelAnimationFrame(rafRef.current);
    };
  }, []);

  const togglePlay = () => {
    const h = howlRef.current;
    if (!h) return;
    if (h.playing()) h.pause();
    else h.play();
  };

  const seek = (e) => {
    const h = howlRef.current;
    if (!h || !duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    h.seek(ratio * duration);
    setLocalProgress(ratio);
  };

  const handleNext = () => { howlRef.current?.stop(); next(); };
  const handlePrev = () => { howlRef.current?.stop(); prev(); };

  const sendFeedback = async (type) => {
    if (!session) return;
    try { await radioApi.feedback({ sessionId: session.id, songId: song?.id, feedbackType: type }); }
    catch {}
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
    <div data-mood={mood} className="mfm" style={{ height: '100vh', overflow: 'hidden', position: 'relative' }}>
      {/* Backdrop */}
      <div style={{
        position: 'absolute', inset: 0,
        background: `radial-gradient(circle at 20% 25%, var(--mood-a) 0%, transparent 55%),
                     radial-gradient(circle at 80% 20%, var(--mood-b) 0%, transparent 55%),
                     radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
                     radial-gradient(circle at 10% 80%, var(--mood-d) 0%, transparent 65%)`,
        backgroundColor: 'var(--mood-d)', filter: 'saturate(1.1)',
      }} />
      <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.18)' }} />

      <div style={{ position: 'relative', zIndex: 2, color: '#fff', height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Top */}
        <div style={{ padding: '24px 56px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.12)' }}>
          <button onClick={() => navigate('/home')} className="row" style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer', gap: 8 }}>
            <Icon.chevL /> <span className="mono" style={{ fontSize: 11, letterSpacing: '.16em' }}>BACK</span>
          </button>
          <div style={{ textAlign: 'center' }}>
            <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: .7 }}>
              TRACK {currentIndex + 1} / {queue.length} · NOW PLAYING
            </div>
            <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 26, marginTop: 2 }}>
              {session?.moodSummary || 'Your Radio'}
            </div>
          </div>
          <button className="row" style={{ background: 'transparent', border: 'none', color: '#fff', cursor: 'pointer' }}>
            <Icon.more />
          </button>
        </div>

        {/* Main */}
        <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1.1fr', gap: 48, padding: '48px 56px', alignItems: 'center' }}>
          {/* Cover */}
          <div style={{ display: 'flex', justifyContent: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', width: 480, height: 480, borderRadius: '50%', border: '1px solid rgba(255,255,255,0.18)', animation: 'pulse-ring 4s ease-out infinite' }} />
            {song.coverUrl ? (
              <img src={song.coverUrl} alt="cover"
                style={{ width: 440, height: 440, borderRadius: '50%', objectFit: 'cover', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }} />
            ) : (
              <MoodBlob size={440} drift={playing} geometry="circle" />
            )}
          </div>

          {/* Info */}
          <div>
            <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: .7 }}>
              {song.platform || 'NETEASE'} · {loadingUrl ? 'LOADING…' : ''}
            </div>
            <h1 className="display" style={{ fontSize: 72, margin: '10px 0 0', lineHeight: 1 }}>
              {song.title}<em style={{ opacity: .5 }}>.</em>
            </h1>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 22, marginTop: 8, opacity: .9 }}>
              {song.artist}
              {song.album && <span style={{ opacity: .6, fontSize: 16 }}> · {song.album}</span>}
            </div>

            {/* AI reason */}
            {song.recommendReason && (
              <div style={{ marginTop: 20, padding: 16, background: 'rgba(255,255,255,0.08)', backdropFilter: 'blur(20px)', borderRadius: 16, border: '1px solid rgba(255,255,255,0.14)' }}>
                <div className="mono" style={{ fontSize: 10, letterSpacing: '.18em', opacity: .7 }}>AI · WHY THIS SONG</div>
                <div style={{ marginTop: 8, fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.65, opacity: .95 }}>
                  {song.recommendReason}
                </div>
              </div>
            )}

            {/* Progress bar */}
            <div style={{ marginTop: 24 }}>
              <div
                onClick={seek}
                style={{ height: 4, background: 'rgba(255,255,255,0.2)', borderRadius: 2, position: 'relative', cursor: 'pointer' }}
              >
                <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${progress * 100}%`, background: '#fff', borderRadius: 2 }} />
                <div style={{ position: 'absolute', left: `${progress * 100}%`, top: -4, width: 12, height: 12, borderRadius: '50%', background: '#fff', transform: 'translateX(-50%)', boxShadow: '0 2px 8px rgba(0,0,0,0.3)' }} />
              </div>
              <div className="mono between" style={{ fontSize: 11, marginTop: 8, opacity: .7 }}>
                <span>{formatTime(currentSeconds)}</span>
                <span>{formatTime(duration)}</span>
              </div>
            </div>

            {/* Controls */}
            <div className="row" style={{ marginTop: 20, gap: 14 }}>
              <button style={ctrlBtn} onClick={handlePrev}><Icon.prev /></button>
              <button onClick={togglePlay} disabled={loadingUrl}
                style={{ ...ctrlBtn, width: 64, height: 64, background: '#fff', color: 'var(--ink)', opacity: loadingUrl ? 0.6 : 1 }}>
                {playing ? <Icon.pause /> : <Icon.play />}
              </button>
              <button style={ctrlBtn} onClick={handleNext}><Icon.next /></button>
              <button style={{ ...ctrlBtn, marginLeft: 12 }} onClick={() => { sendFeedback('SKIP'); handleNext(); }}>
                <Icon.skip />
              </button>
            </div>

            {/* Actions */}
            <div className="row" style={{ marginTop: 20, gap: 8, flexWrap: 'wrap' }}>
              <ChipDark active={liked} onClick={() => { setLiked(x => !x); sendFeedback('LIKE'); }}>
                <Icon.heart style={{ fill: liked ? 'var(--mood-a)' : 'none', stroke: liked ? 'var(--mood-a)' : 'currentColor' }} />
                {liked ? '已红心' : '红心'}
              </ChipDark>
              <ChipDark onClick={() => { sendFeedback('DISLIKE'); handleNext(); }}>不喜欢</ChipDark>
              <ChipDark>队列 · {queue.length - currentIndex - 1}</ChipDark>
              <ChipDark><Icon.share /> 分享</ChipDark>
            </div>
          </div>
        </div>

        {/* Queue strip */}
        <div style={{ padding: '16px 56px 20px', borderTop: '1px solid rgba(255,255,255,0.12)', display: 'flex', gap: 12, alignItems: 'center', overflowX: 'auto' }}>
          <div className="mono" style={{ fontSize: 10, letterSpacing: '.18em', opacity: .6, whiteSpace: 'nowrap' }}>
            {upcoming.length > 0 ? 'UP NEXT →' : 'QUEUE'}
          </div>
          {upcoming.map((s, i) => (
            <div key={i} className="row" style={{ gap: 8, padding: '6px 12px', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 999, whiteSpace: 'nowrap', flexShrink: 0 }}>
              <span className="mono" style={{ fontSize: 10, opacity: .6 }}>0{currentIndex + i + 2}</span>
              <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 13 }}>{s.title}</span>
              <span style={{ fontSize: 12, opacity: .6 }}>· {s.artist}</span>
            </div>
          ))}
          {session?.playlistId && (
            <button
              onClick={() => navigate(`/playlist/${session.playlistId}`)}
              style={{ marginLeft: 'auto', flexShrink: 0, background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)', color: '#fff', borderRadius: 999, padding: '6px 14px', cursor: 'pointer', fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '.14em', whiteSpace: 'nowrap' }}
            >
              歌单详情 →
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
