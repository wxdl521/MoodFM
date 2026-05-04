import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('moodfm_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res.data,
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
