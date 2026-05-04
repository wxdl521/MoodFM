import api from './client';

export const reportsApi = {
  getWeeklyReport: (id) => api.get(`/reports/weekly/${id}`),
  listReports:     ()   => api.get('/reports/weekly'),
  generate:        ()   => api.post('/reports/weekly/generate'),
};
