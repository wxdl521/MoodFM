import api from './client';

export const userApi = {
  me: () => api.get('/users/me'),
  savePreferences: (prefs) => api.put('/users/preferences', prefs),
};
