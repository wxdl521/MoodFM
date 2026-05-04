import { create } from 'zustand';

export const MOOD_OPTIONS = [
  { id: 'dusk',       label: '暮色', colors: ['#ff8a5b', '#6e5cd9'] },
  { id: 'melancholy', label: '忧郁', colors: ['#4a6fa5', '#2d3a8c'] },
  { id: 'energetic',  label: '高能', colors: ['#ffd23f', '#e85a8a'] },
  { id: 'focused',    label: '专注', colors: ['#7fb069', '#2d3a8c'] },
  { id: 'calm',       label: '平静', colors: ['#c9d6c0', '#6e7a8a'] },
];

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme === 'dark' ? 'dark' : '');
}

// Apply saved theme immediately before first render
const savedTheme = localStorage.getItem('moodfm_theme') || 'light';
const savedMood  = localStorage.getItem('moodfm_mood')  || 'dusk';
applyTheme(savedTheme);

export const useUIStore = create((set, get) => ({
  theme: savedTheme,
  mood:  savedMood,

  toggleTheme: () => {
    const next = get().theme === 'light' ? 'dark' : 'light';
    localStorage.setItem('moodfm_theme', next);
    applyTheme(next);
    set({ theme: next });
  },

  setMood: (mood) => {
    localStorage.setItem('moodfm_mood', mood);
    set({ mood });
  },
}));
