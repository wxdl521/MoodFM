// src/hooks/useAudioPlayer.js
import { useState, useEffect, useRef, useCallback } from 'react';
import { Howl } from 'howler';
import { usePlayerStore } from '../store/playerStore';
import { useRadioStore } from '../store/radioStore';
import { radioApi } from '../api/radio';

export function useAudioPlayer() {
  const { queue, currentIndex, session, next, prev, setPlaying } = usePlayerStore();
  const { recordSkip, resetSkipStreak, pushFeedback } = useRadioStore();

  const song = queue[currentIndex];

  const howlRef    = useRef(null);
  const rafRef     = useRef(null);
  const loadIdRef  = useRef(0);

  const [playing,    setLocalPlaying]  = useState(false);
  const [progress,   setLocalProgress] = useState(0);
  const [duration,   setDuration]      = useState(0);
  const [loadingUrl, setLoadingUrl]    = useState(false);

  const destroyHowl = useCallback(() => {
    loadIdRef.current++;
    if (howlRef.current) {
      howlRef.current.off();
      howlRef.current.stop();
      howlRef.current.unload();
      howlRef.current = null;
    }
    cancelAnimationFrame(rafRef.current);
    setLocalPlaying(false);
    setLocalProgress(0);
    setDuration(0);
  }, []);

  const loadSong = useCallback(async (s) => {
    if (!s) return;
    destroyHowl();
    const myId = loadIdRef.current;
    setLoadingUrl(true);
    try {
      const url = await radioApi.getSongUrl(s.platform, s.platformSongId);
      if (myId !== loadIdRef.current) return;
      const howl = new Howl({
        src: [typeof url === 'string' ? url : url?.data ?? url],
        html5: true,
        onload: () => {
          if (myId !== loadIdRef.current) return;
          setDuration(howl.duration());
          setLoadingUrl(false);
        },
        onloaderror: () => {
          if (myId !== loadIdRef.current) return;
          setLoadingUrl(false);
        },
        onplay: () => {
          if (myId !== loadIdRef.current) return;
          setLocalPlaying(true);
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
          setLocalPlaying(false);
          setPlaying(false);
          cancelAnimationFrame(rafRef.current);
        },
        onstop: () => {
          if (myId !== loadIdRef.current) return;
          setLocalPlaying(false);
          setPlaying(false);
          cancelAnimationFrame(rafRef.current);
        },
        onend: () => {
          if (myId !== loadIdRef.current) return;
          cancelAnimationFrame(rafRef.current);
          if (session?.id && s.id) {
            radioApi.feedback({ sessionId: session.id, songId: s.id, feedbackType: 'COMPLETE' }).catch(() => {});
          }
          next();
        },
      });
      howlRef.current = howl;
      howl.play();
    } catch (e) {
      if (myId === loadIdRef.current) { console.error('[useAudioPlayer] loadSong error', e); setLoadingUrl(false); }
    }
  }, [destroyHowl, next, session, setPlaying]);

  // Reload when song changes
  useEffect(() => {
    if (song) loadSong(song);
    return () => cancelAnimationFrame(rafRef.current);
  }, [currentIndex]); // eslint-disable-line react-hooks/exhaustive-deps

  // Cleanup on unmount
  useEffect(() => {
    return () => destroyHowl();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const play  = () => howlRef.current?.play();
  const pause = () => howlRef.current?.pause();
  const seek  = (ratio) => {
    const h = howlRef.current;
    if (!h || !duration) return;
    h.seek(ratio * duration);
    setLocalProgress(ratio);
  };

  const skip = () => {
    const h    = howlRef.current;
    const pos  = h ? Math.round(h.seek()) : 0;
    const type = pos < 30 ? 'SKIP_EARLY' : 'SKIP';
    if (session?.id && song?.id) {
      radioApi.feedback({ sessionId: session.id, songId: song.id, feedbackType: type }).catch(() => {});
    }
    const shouldPrompt = recordSkip(song?.artist ?? '');
    // Return whether to show the blacklist banner — caller decides UI
    destroyHowl();
    next();
    return shouldPrompt;
  };

  const like = () => {
    if (session?.id && song?.id) {
      radioApi.feedback({ sessionId: session.id, songId: song.id, feedbackType: 'LIKE' }).catch(() => {});
    }
    resetSkipStreak();
  };

  const dislike = () => {
    if (session?.id && song?.id) {
      radioApi.feedback({ sessionId: session.id, songId: song.id, feedbackType: 'DISLIKE' }).catch(() => {});
    }
    destroyHowl();
    next();
  };

  const handlePrev = () => {
    destroyHowl();
    prev();
  };

  return { play, pause, seek, skip, like, dislike, prev: handlePrev, playing, progress, duration, loadingUrl };
}
