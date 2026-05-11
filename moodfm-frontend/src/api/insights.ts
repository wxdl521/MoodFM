import api from './client';

interface InsightsStats {
  totalListeningTime: string;
  topGenre: string;
  topGenreRatio: string;
  aiAccuracy: string;
  moodKey: string;
  moodSub: string;
  weekLabel: string;
  weekRange: string;
  listeningVsLast: string;
}

interface ReportCard { id: number; week: number; year: number; title: string; mood: string }

interface InsightsSummary {
  headline: string;
  headlineAlt: string;
  headlineCn: string;
  stats: InsightsStats;
  aiSummary: { title: string; body: string; reportId: number } | null;
  previousReports: ReportCard[];
  essayTitle: string;
  essayBody: string;
  weeklyReportId: number | null;
}

interface MoodTrendVO { labels: string[]; userMood: number[]; songMood: number[]; lowPointIndex: number }
interface GenreRadarItem { genre: string; genreCn: string; value: number }
interface TopItemsResponse {
  artists: Array<{ name: string; tag: string; totalTime: string; proportion: number }>;
  songs: Array<{ title: string; artist: string; playCount: number; proportion: number }>;
}

interface CalendarDayVO { day: number; mood: string; tracks: number; minutes: number; sessions: number; future: boolean; empty: boolean }
interface CalendarMonthVO { year: number; month: number; firstDayOfWeek: number; daysInMonth: number; days: CalendarDayVO[] }
interface DayDetailVO { day: number; mood: string; tracks: number; minutes: number; sessions: number; topTracks: Array<{ title: string; artist: string; durationFormatted: string; playCount: number }> }

export const insightsApi = {
  getSummary:    (days = 7): Promise<InsightsSummary>    => api.get(`/insights/summary?days=${days}`),
  getMoodTrend:  (days = 7): Promise<MoodTrendVO>        => api.get(`/insights/mood-trend?days=${days}`),
  getGenreRadar: (days = 7): Promise<GenreRadarItem[]>   => api.get(`/insights/genre-radar?days=${days}`),
  getTopItems:   (days = 7): Promise<TopItemsResponse>   => api.get(`/insights/top-items?days=${days}`),

  getCalendar:  (year: number, month: number): Promise<CalendarMonthVO>  =>
    api.get(`/insights/calendar?year=${year}&month=${month}`),
  getDayDetail: (date: string): Promise<DayDetailVO> =>
    api.get(`/insights/day/${date}`),
};
