import api from './client';

interface User { id: number; username: string; email: string; avatarUrl?: string }
interface UserPreferences { theme: 'light' | 'dark'; defaultPlatform: 'netease' | 'qqmusic'; autoPlay: boolean; crossfadeDuration: number }
interface NotificationSettings { weeklyReport: boolean; cookieExpiry: boolean; newFeatures: boolean; weeklyReportDay?: number; weeklyReportHour?: number }

interface UpdateProfileBody { username?: string; email?: string }
interface ChangePasswordBody { oldPassword: string; newPassword: string }
interface Device { id: string; deviceName: string; lastActive: string }

export const userApi = {
  getProfile:        (): Promise<User>                      => api.get('/user/profile'),
  updateProfile:     (body: UpdateProfileBody): Promise<User> => api.put('/user/profile', body),
  changePassword:    (body: ChangePasswordBody): Promise<void> => api.post('/user/password', body),
  uploadAvatar:      (file: File): Promise<{ avatarUrl: string }> => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post('/user/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  getPreferences:    (): Promise<UserPreferences>                    => api.get('/user/preferences'),
  updatePreferences: (body: Partial<UserPreferences>): Promise<UserPreferences> => api.put('/user/preferences', body),
  getNotificationSettings:    (): Promise<NotificationSettings>               => api.get('/user/notifications/settings'),
  updateNotificationSettings: (body: Partial<NotificationSettings>): Promise<void> => api.put('/user/notifications/settings', body),
  getDevices:        (): Promise<Device[]>                           => api.get('/user/devices'),
  revokeDevice:      (id: string): Promise<void>                     => api.delete(`/user/devices/${id}`),
  deleteAccount:     (): Promise<void>                               => api.delete('/user/account'),
  deleteAllData:     (): Promise<void>                               => api.delete('/user/data/all'),
};
