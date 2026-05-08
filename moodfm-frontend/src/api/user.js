import api from './client';

export const userApi = {
  getProfile:        ()     => api.get('/user/profile'),
  updateProfile:     (body) => api.put('/user/profile', body),
  changePassword:    (body) => api.post('/user/password', body),
  uploadAvatar:      (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post('/user/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  getPreferences:    ()     => api.get('/user/preferences'),
  updatePreferences: (body) => api.put('/user/preferences', body),
  getDevices:        ()     => api.get('/user/devices'),
  revokeDevice:      (id)   => api.delete(`/user/devices/${id}`),
  deleteAccount:     ()     => api.delete('/user/account'),
};
