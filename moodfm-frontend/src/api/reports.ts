import api from './client';

interface WeeklyReport {
  id: number;
  weekLabel: string;
  dateRange: string;
  titleEn1: string;
  headlineWord: string;
  headlineWord2: string;
  titleCn: string;
  summary: string;
  essayBody: string;
  quote: string;
  stats: { tracks: string; listenTime: string; newDiscovered: string; moodMatch: string };
  mostPlayedSong: { title: string; artist: string; playCount: number } | null;
  topDiscoveries: Array<{ title: string; artist: string; mood: string; genre: string }>;
}

interface WeeklyReportSummary {
  id: number;
  weekStart: string;
  weekEnd: string;
}

interface AnnualReport {
  year: number;
  totalListeningTime: string;
  totalTracks: number;
  totalSessions: number;
  topGenre: string;
  topMood: string;
  topArtists: Array<{ name: string; totalTime: string; playCount: number }>;
  topSongs: Array<{ title: string; artist: string; playCount: number }>;
  monthlyStats: Array<{ month: number; tracks: number; minutes: number }>;
}

export const reportsApi = {
  getWeeklyReport: (id: string): Promise<WeeklyReport>          => api.get(`/reports/weekly/${id}`),
  listReports:     (): Promise<WeeklyReportSummary[]>            => api.get('/reports/weekly'),
  generate:        (): Promise<WeeklyReport>                     => api.post('/reports/weekly/generate'),
  getAnnualReport: (year: number): Promise<AnnualReport>         => api.get(`/reports/annual/${year}`),
};
