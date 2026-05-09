import api from './client';

interface BlacklistEntry {
  id: string;
  type: 'artist' | 'song' | 'keyword';
  value: string;
  label?: string;
}

interface AddBlacklistEntry {
  // entry: { type: 'artist'|'song'|'keyword', value: string, label?: string }
  type: 'artist' | 'song' | 'keyword';
  value: string;
  label?: string;
}

export const blacklistApi = {
  getAll: (): Promise<BlacklistEntry[]>                       => api.get('/user/blacklist'),
  add:    (entry: AddBlacklistEntry): Promise<BlacklistEntry> => api.post('/user/blacklist', entry),
  remove: (id: string): Promise<void>                        => api.delete(`/user/blacklist/${id}`),
};
