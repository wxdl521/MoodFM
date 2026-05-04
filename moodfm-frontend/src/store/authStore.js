import { create } from 'zustand';

const stored = localStorage.getItem('moodfm_user');

export const useAuthStore = create((set) => ({
  user: stored ? JSON.parse(stored) : null,
  token: localStorage.getItem('moodfm_token') || null,

  login: (user, token) => {
    localStorage.setItem('moodfm_token', token);
    localStorage.setItem('moodfm_user', JSON.stringify(user));
    set({ user, token });
  },

  logout: () => {
    localStorage.removeItem('moodfm_token');
    localStorage.removeItem('moodfm_user');
    set({ user: null, token: null });
  },

  setUser: (user) => {
    localStorage.setItem('moodfm_user', JSON.stringify(user));
    set({ user });
  },
}));
