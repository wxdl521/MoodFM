import { create } from 'zustand';

export const usePlayerStore = create((set, get) => ({
  session: null,   // { id, moodSummary, scene, playlistId? }
  queue: [],
  currentIndex: 0,
  playing: false,
  progress: 0,
  mood: 'dusk',

  setSession: (session, queue) => set({ session, queue, currentIndex: 0, playing: false, progress: 0 }),

  setPlaying: (playing) => set({ playing }),
  setProgress: (progress) => set({ progress }),

  setMood: (mood) => set({ mood }),

  next: () => {
    const { queue, currentIndex } = get();
    if (currentIndex < queue.length - 1) {
      set({ currentIndex: currentIndex + 1, progress: 0 });
    }
  },

  prev: () => {
    const { currentIndex } = get();
    if (currentIndex > 0) set({ currentIndex: currentIndex - 1, progress: 0 });
  },

  appendQueue: (songs) => set(s => ({ queue: [...s.queue, ...songs] })),

  setCurrentIndex: (index) => set({ currentIndex: index, progress: 0 }),
}));
