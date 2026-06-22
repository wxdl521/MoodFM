import api from './client';
import type { MoodSessionVO } from '@/types';

interface SongVO { id: number; title: string; artist: string; album?: string; durationSeconds: number; coverUrl?: string; platform?: string; platformSongId?: string; playUrl?: string; recommendReason?: string }

interface RadioQueueVO { sessionId: number; scene?: string; moodSummary?: string; songs: SongVO[]; totalCount: number }

// 已知后端识别 completed/skip，其余字符串透传写入 feedback_event 表
// 这里把调用方用到的事件枚举固化，避免字面量散落各处写错
export type FeedbackEvent = 'volume_up' | 'skip' | 'completed' | 'dislike' | 'like'

// sessionId/songId 在后端 DTO 是 Long，前端各调用点统一在传入前用 Number(...) 转换
interface FeedbackPayload { sessionId: number; songId: number; playedSeconds?: number; totalSeconds?: number; platform?: string }

interface StartRadioData { moodText: string; scene?: string; platform?: 'netease' | 'qqmusic'; durationMinutes?: number | null }

export const radioApi = {
  startRadio:      (data: StartRadioData): Promise<RadioQueueVO>          => api.post('/radio/start', data),
  startFromSong:   (songId: string): Promise<RadioQueueVO>                => api.post('/radio/start-from-song', { songId: Number(songId) }),
  playFromPlaylist: (playlistId: string, durationMinutes?: number | null): Promise<RadioQueueVO> =>
    api.post('/radio/play-from-playlist', { playlistId, durationMinutes }),
  sendFeedback:    (event: FeedbackEvent, payload: FeedbackPayload): Promise<void> => api.post('/radio/feedback', { ...payload, eventType: event }),
  getSessions:     (limit = 5): Promise<MoodSessionVO[]>                  => api.get('/radio/sessions', { params: { limit } }),
  getSongUrl:      (platform: string, songId: string): Promise<string>    => api.get(`/radio/url?platform=${platform}&songId=${songId}`),
};
