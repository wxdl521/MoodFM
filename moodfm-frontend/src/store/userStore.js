import { create } from 'zustand';
import { userApi } from '../api/user';

export const useUserStore = create((set) => ({
  profile: null,      // { username, email, phone, avatarUrl }
  preferences: null,  // { defaultPlatform, preferredGenres, preferredLanguages, defaultScene }
  loading: false,
  error: null,

  fetchProfile: async () => {
    set({ loading: true, error: null });
    try {
      const data = await userApi.getProfile();
      set({ profile: data, loading: false });
    } catch (e) {
      set({ error: e?.message ?? '加载失败', loading: false });
    }
  },

  updateProfile: async (fields) => {
    try {
      const data = await userApi.updateProfile(fields);
      set({ profile: data });
      return { ok: true };
    } catch (e) {
      return { ok: false, error: e?.message ?? '保存失败' };
    }
  },

  uploadAvatar: async (file) => {
    try {
      const data = await userApi.uploadAvatar(file);
      // data should be { avatarUrl: '...' } or just the URL string
      const avatarUrl = typeof data === 'string' ? data : data?.avatarUrl;
      set(s => ({ profile: { ...s.profile, avatarUrl } }));
      return { ok: true };
    } catch (e) {
      return { ok: false, error: e?.message ?? '上传失败' };
    }
  },

  fetchPreferences: async () => {
    try {
      const data = await userApi.getPreferences();
      set({ preferences: data });
    } catch {}
  },

  updatePreferences: async (fields) => {
    try {
      const data = await userApi.updatePreferences(fields);
      set({ preferences: data });
      return { ok: true };
    } catch (e) {
      return { ok: false, error: e?.message ?? '保存失败' };
    }
  },

  resetStore: () => set({ profile: null, preferences: null, loading: false, error: null }),
}));
