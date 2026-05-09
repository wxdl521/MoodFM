import api from './client';

interface InsightsSummary {
  totalSongs: number;
  totalMinutes: number;
  topMood: string;
  topGenre: string;
  streakDays: number;
}

interface MoodTrendPoint { date: string; mood: string; score: number }
interface GenreRadarItem { genre: string; value: number }
interface TopItemsResponse { songs: Array<{ song: { id: string; title: string; artist: string }; playCount: number }>; artists: Array<{ name: string; playCount: number }> }

interface CalendarDay { date: string; hasMood: boolean; mood?: string; songCount: number }
interface DayDetailResponse { date: string; songs: Array<{ id: string; title: string; artist: string; playedAt: string }>; moods: string[] }

export const insightsApi = {
  getSummary:    (days = 7): Promise<InsightsSummary>    => api.get(`/insights/summary?days=${days}`),
  getMoodTrend:  (days = 7): Promise<MoodTrendPoint[]>   => api.get(`/insights/mood-trend?days=${days}`),
  getGenreRadar: (days = 7): Promise<GenreRadarItem[]>   => api.get(`/insights/genre-radar?days=${days}`),
  getTopItems:   (days = 7): Promise<TopItemsResponse>   => api.get(`/insights/top-items?days=${days}`),

  getCalendar:  (year: number, month: number): Promise<CalendarDay[]>  =>
    api.get(`/insights/calendar?year=${year}&month=${month}`),
  getDayDetail: (date: string): Promise<DayDetailResponse> =>
    api.get(`/insights/day/${date}`),
};
