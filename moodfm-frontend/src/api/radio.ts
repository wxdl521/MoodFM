import api from './client';
import type { MoodSessionVO } from '@/types';

interface SongVO { id: number; title: string; artist: string; album?: string; durationSeconds: number; coverUrl?: string; platform?: string; platformSongId?: string; playUrl?: string; recommendReason?: string }

interface RadioQueueVO { sessionId: number; scene?: string; moodSummary?: string; songs: SongVO[]; totalCount: number }

interface Feedback { songId: string; sessionId: string; eventType: string; playedSeconds?: number; totalSeconds?: number; platform?: string }

interface StartRadioData { moodText: string; scene?: string; platform?: 'netease' | 'qqmusic'; durationMinutes?: number }

export const radioApi = {
  startRadio:      (data: StartRadioData): Promise<RadioQueueVO>          => api.post('/radio/start', data),
  startFromSong:   (songId: string): Promise<RadioQueueVO>                => api.post('/radio/start-from-song', { songId: Number(songId) }),
  feedback:        (data: Feedback): Promise<void>                        => api.post('/radio/feedback', data),
  getSessions:     (limit = 5): Promise<MoodSessionVO[]>                  => api.get('/radio/sessions', { params: { limit } }),
  getSongUrl:      (platform: string, songId: string): Promise<string>    => api.get(`/radio/url?platform=${platform}&songId=${songId}`),
};
