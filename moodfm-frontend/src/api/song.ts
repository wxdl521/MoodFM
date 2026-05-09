import api from './client';

interface Song { id: string; title: string; artist: string; album?: string; platform: 'netease' | 'qqmusic'; platformSongId: string; duration: number; coverUrl?: string; audioUrl?: string }

interface LyricsResponse { lyrics: string; tlyrics?: string }

export const songApi = {
  get:     (id: string): Promise<Song>           => api.get(`/songs/${id}`),
  similar: (id: string): Promise<Song[]>         => api.get(`/songs/${id}/similar`),
  lyrics:  (id: string): Promise<LyricsResponse> => api.get(`/songs/${id}/lyrics`),
};
