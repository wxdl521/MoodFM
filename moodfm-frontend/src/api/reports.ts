import api from './client';

interface WeeklyReport {
  id: string;
  weekStart: string;
  weekEnd: string;
  totalSongs: number;
  totalMinutes: number;
  topMood: string;
  topGenre: string;
  topSongs: Array<{ id: string; title: string; artist: string; playCount: number }>;
  generatedAt: string;
}

export const reportsApi = {
  getWeeklyReport: (id: string): Promise<WeeklyReport>   => api.get(`/reports/weekly/${id}`),
  listReports:     (): Promise<WeeklyReport[]>            => api.get('/reports/weekly'),
  generate:        (): Promise<WeeklyReport>              => api.post('/reports/weekly/generate'),
};
