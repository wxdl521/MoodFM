import api from './client';

type PlatformId = 'netease' | 'qqmusic';

interface PlatformBinding { platform: PlatformId; valid: boolean; platformUsername?: string; isDefault?: boolean; expiresAt?: string }
interface BindPhoneData { phone: string; code: string; ticket: string }

export const platformApi = {
  getBindings: (): Promise<PlatformBinding[]> => api.get('/platforms'),

  bindCookie: (platform: PlatformId, cookie: string): Promise<PlatformBinding> =>
    api.post(`/platforms/${platform}/bind/cookie`, { cookie }),

  sendPhoneCode: (platform: PlatformId, phone: string): Promise<{ ticket: string }> =>
    api.post(`/platforms/${platform}/phone/code`, { phone }),

  bindPhone: (platform: PlatformId, data: BindPhoneData): Promise<PlatformBinding> =>
    api.post(`/platforms/${platform}/bind/phone`, data),

  setDefault: (platform: PlatformId): Promise<void> =>
    api.put(`/platforms/${platform}/default`),

  unbind: (platform: PlatformId): Promise<void> =>
    api.delete(`/platforms/${platform}`),

  generateQR: (platform: PlatformId): Promise<{ key: string; qrimg: string; qrurl: string }> =>
    api.post(`/platforms/${platform}/qr/generate`),

  checkQRStatus: (platform: PlatformId, key: string): Promise<{ status: 'waiting' | 'scanned' | 'success' | 'expired' | 'error'; cookie?: string }> =>
    api.get(`/platforms/${platform}/qr/status?key=${encodeURIComponent(key)}`),
};
