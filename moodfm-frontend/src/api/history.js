import api from './client';

export const historyApi = {
  list: (params) => api.get('/history', { params }),
  // params: { page?, pageSize?, scene?, startDate?, endDate? }
};
