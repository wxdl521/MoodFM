import api from './client';

export interface BlacklistEntry {
  id: string;
  type: 'artist' | 'song' | 'keyword';
  value: string;
  label?: string;
  artistLabel?: string;
  addedAt?: string;
}

export interface AddBlacklistEntry {
  type: 'artist' | 'song' | 'keyword';
  value: string;
  label?: string;
}

export const blacklistApi = {
  getAll: (): Promise<BlacklistEntry[]>                       => api.get('/user/blacklist'),
  add:    (entry: AddBlacklistEntry): Promise<BlacklistEntry> => api.post('/user/blacklist', entry),
  remove: (id: string): Promise<void>                        => api.delete(`/user/blacklist/${id}`),
};
