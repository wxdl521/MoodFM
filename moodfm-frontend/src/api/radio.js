import api from './client';

export const radioApi = {
  startRadio: (data) => api.post('/radio/start', data),
  getQueue: (sessionId) => api.get(`/radio/next?sessionId=${sessionId}`),
  getSongUrl: (platform, songId) => api.get(`/radio/url?platform=${platform}&songId=${songId}`),
  feedback: (data) => api.post('/radio/feedback', data),

  getSessions: (limit = 5) => api.get(`/radio/sessions?limit=${limit}`),

  start: (body) => api.post('/radio/start', body),
  batchFeedback: (body) => api.post('/radio/feedback/batch', body),
};
