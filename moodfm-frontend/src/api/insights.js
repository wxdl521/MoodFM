import api from './client';

export const insightsApi = {
  getSummary:    (days = 7) => api.get(`/insights/summary?days=${days}`),
  getMoodTrend:  (days = 7) => api.get(`/insights/mood-trend?days=${days}`),
  getGenreRadar: (days = 7) => api.get(`/insights/genre-radar?days=${days}`),
  getTopItems:   (days = 7) => api.get(`/insights/top-items?days=${days}`),

  getCalendar:  (year, month) => api.get(`/insights/calendar?year=${year}&month=${month}`),
  getDayDetail: (date)        => api.get(`/insights/day/${date}`),
};
