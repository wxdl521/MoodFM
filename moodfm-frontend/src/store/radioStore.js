import { create } from 'zustand';
import { radioApi } from '../api/radio';

export const useRadioStore = create((set, get) => ({
  // Session metadata (complements playerStore's session field)
  sessionId: null,
  moodSummary: '',

  // Feedback buffer — events accumulate here between flushes
  feedbackBuffer: [],

  // Skip streak tracker — used by Player to trigger blacklist prompt
  skipStreak: 0,
  lastSkippedArtist: null,

  // Call when starting a new radio session
  // Returns { session, queue } to be passed to playerStore.setSession()
  startSession: async (payload) => {
    // payload: { text?, scene?, valence?, energy? }
    const res = await radioApi.start(payload);
    set({
      sessionId: res.session?.id ?? null,
      moodSummary: res.session?.moodSummary ?? '',
      feedbackBuffer: [],
      skipStreak: 0,
      lastSkippedArtist: null,
    });
    return res; // caller does: playerStore.setSession(res.session, res.queue)
  },

  // Queue a feedback event (called by Player on skip/like/dislike/complete)
  pushFeedback: (event) => {
    // event: { songId, eventType, playedSeconds?, totalSeconds?, sessionId }
    set(s => ({ feedbackBuffer: [...s.feedbackBuffer, event] }));
  },

  // Flush buffered feedback to backend (call after every 3 songs or on session end)
  flushFeedback: async () => {
    const { feedbackBuffer } = get();
    if (!feedbackBuffer.length) return;
    try {
      await radioApi.batchFeedback({ events: feedbackBuffer });
      set({ feedbackBuffer: [] });
    } catch (e) {
      console.error('[radioStore] flushFeedback error', e);
    }
  },

  // Track consecutive skips — returns true if streak hits 3
  recordSkip: (artistName) => {
    const next = get().skipStreak + 1;
    set({ skipStreak: next, lastSkippedArtist: artistName });
    if (next >= 3) {
      set({ skipStreak: 0 }); // reset after triggering
      return true; // caller should show blacklist banner
    }
    return false;
  },

  // Reset skip streak on non-skip events (like/complete)
  resetSkipStreak: () => set({ skipStreak: 0, lastSkippedArtist: null }),

  // Clean up on session end
  resetSession: () => set({
    sessionId: null,
    moodSummary: '',
    feedbackBuffer: [],
    skipStreak: 0,
    lastSkippedArtist: null,
  }),
}));
