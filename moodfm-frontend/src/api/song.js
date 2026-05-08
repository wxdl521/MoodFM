import api from './client';

export const songApi = {
  get:     (id) => api.get(`/songs/${id}`),
  similar: (id) => api.get(`/songs/${id}/similar`),
  lyrics:  (id) => api.get(`/songs/${id}/lyrics`),
};
