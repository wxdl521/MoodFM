import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
});

let isRefreshing = false;
let refreshQueue: Array<(token: string | null) => void> = [];

function processQueue(token: string | null) {
  refreshQueue.forEach(cb => cb(token));
  refreshQueue = [];
}

function clearAuthAndRedirect() {
  localStorage.removeItem('moodfm_token');
  localStorage.removeItem('moodfm_user');
  localStorage.removeItem('moodfm_refresh_token');
  window.location.href = '/auth';
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('moodfm_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if ('data' in body) return body.data
      return Promise.reject({ message: body.message || '请求失败', code: body.code })
    }
    return body
  },
  async (err) => {
    const status = err.response?.status;
    const originalRequest = err.config;

    if (status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('moodfm_refresh_token');
      if (!refreshToken) {
        clearAuthAndRedirect();
        return Promise.reject(err.response?.data || err);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          refreshQueue.push((newToken) => {
            if (newToken) {
              originalRequest.headers.Authorization = `Bearer ${newToken}`;
              resolve(api(originalRequest));
            } else {
              reject(err);
            }
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const res = await axios.post('/api/auth/refresh', { refreshToken });
        const body = res.data;
        const data = (body && typeof body === 'object' && 'data' in body) ? body.data : body;
        const newAccessToken = data.accessToken;
        localStorage.setItem('moodfm_token', newAccessToken);
        if (data.refreshToken) localStorage.setItem('moodfm_refresh_token', data.refreshToken);
        api.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
        // 同步更新 Pinia authStore（避免 store 中的 token 与实际使用的 token 不同步）
        // 使用动态 import 避免循环依赖（auth store → auth api → client）
        import('@/stores/auth').then(({ useAuthStore }) => {
          useAuthStore().setTokens(newAccessToken, data.refreshToken)
        });
        processQueue(newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
      } catch {
        processQueue(null);
        clearAuthAndRedirect();
        return Promise.reject(err.response?.data || err);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(err.response?.data || err);
  }
);

export default api;
