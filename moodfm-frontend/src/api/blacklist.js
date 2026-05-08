import api from './client';

export const blacklistApi = {
  getAll: ()       => api.get('/user/blacklist'),
  add:    (entry)  => api.post('/user/blacklist', entry),
  // entry: { type: 'artist'|'song'|'keyword', value: string, label?: string }
  remove: (id)     => api.delete(`/user/blacklist/${id}`),
};
