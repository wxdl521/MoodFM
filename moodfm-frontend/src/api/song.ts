import api from './client';

interface Song { id: string; title: string; artist: string; album?: string; platform: 'netease' | 'qqmusic'; platformSongId: string; duration: number; coverUrl?: string; audioUrl?: string }

export interface LyricLine { time: number; text: string }

export const songApi = {
  get:     (id: string): Promise<Song>        => api.get(`/songs/${id}`),
  similar: (id: string): Promise<Song[]>      => api.get(`/songs/${id}/similar`),
  lyrics:  (id: string): Promise<LyricLine[]> => api.get(`/songs/${id}/lyrics`),
};
