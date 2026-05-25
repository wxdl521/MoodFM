import api from './client';

export interface HistorySong { id: string; title: string; artist: string; album?: string; platform: 'netease' | 'qqmusic'; platformSongId: string; duration: number; coverUrl?: string; audioUrl?: string }
export interface PlayHistory { id: string; song: HistorySong; playedAt: string; sessionId?: string; durationPlayed: number }

export interface HistoryParams {
  page?: number;
  pageSize?: number;
  scene?: string;
  startDate?: string;
  endDate?: string;
}

export interface HistoryListResponse { items: PlayHistory[]; total: number; page: number; pageSize: number }

export const historyApi = {
  list: (params?: HistoryParams): Promise<HistoryListResponse> => api.get('/history', { params }),
  clear: (): Promise<void> => api.delete('/history/all'),
};
