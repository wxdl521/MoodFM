import api from './client';

export const platformApi = {
  getBindings: () => api.get('/platforms'),

  generateQR: (platform) => api.post(`/platforms/${platform}/qr/generate`),

  checkQRStatus: (platform, key) => api.get(`/platforms/${platform}/qr/status?key=${encodeURIComponent(key)}`),

  bindCookie: (platform, cookie) =>
    api.post(`/platforms/${platform}/bind/cookie?cookie=${encodeURIComponent(cookie)}`),

  sendPhoneCode: (platform, phone) => api.post(`/platforms/${platform}/phone/code`, { phone }),

  bindPhone: (platform, data) => api.post(`/platforms/${platform}/bind/phone`, data),

  setDefault: (platform) => api.put(`/platforms/${platform}/default`),

  unbind: (platform) => api.delete(`/platforms/${platform}`),
};
