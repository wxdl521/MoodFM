import api from './client';

interface Song { id: string; title: string; artist: string; album?: string; platform: 'netease' | 'qqmusic'; platformSongId: string; duration: number; coverUrl?: string; audioUrl?: string }
interface RadioSession { sessionId: string; moodText: string; scene?: string; status: 'active' | 'paused' | 'ended'; createdAt: string }
interface Feedback { songId: string; sessionId: string; action: 'like' | 'dislike' | 'skip' | 'love' | 'blacklist' }

interface StartRadioData { moodText: string; scene?: string; platform?: 'netease' | 'qqmusic' }
interface QueueResponse { songs: Song[]; sessionId: string }
interface SongUrlResponse { url: string; expireAt?: string }

export const radioApi = {
  startRadio:    (data: StartRadioData): Promise<RadioSession>          => api.post('/radio/start', data),
  getQueue:      (sessionId: string): Promise<QueueResponse>            => api.get(`/radio/next?sessionId=${sessionId}`),
  getSongUrl:    (platform: 'netease' | 'qqmusic', songId: string): Promise<SongUrlResponse> =>
    api.get(`/radio/url?platform=${platform}&songId=${songId}`),
  feedback:      (data: Feedback): Promise<void>                        => api.post('/radio/feedback', data),
  batchFeedback: (body: { feedbacks: Feedback[] }): Promise<void>       => api.post('/radio/feedback/batch', body),
  getSessions:   (limit = 5): Promise<RadioSession[]>                   => api.get(`/radio/sessions?limit=${limit}`),
};
