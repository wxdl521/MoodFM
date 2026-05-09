import axios, { AxiosInstance } from 'axios';

const api: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('moodfm_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => {
    const body = res.data
    // Unwrap Spring R<T> envelope: { code, message, data }
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      return body.data
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('moodfm_token');
      localStorage.removeItem('moodfm_user');
      window.location.href = '/auth';
    }
    return Promise.reject(err.response?.data || err);
  }
);

export default api;
