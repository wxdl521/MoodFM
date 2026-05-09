import api from './client';

type PlatformId = 'netease' | 'qqmusic';

interface PlatformBinding { platform: PlatformId; bound: boolean; nickname?: string; avatarUrl?: string }
interface QRGenerateResponse { key: string; qrUrl: string }
interface QRStatusResponse { status: 'waiting' | 'scanned' | 'confirmed' | 'expired'; cookie?: string }
interface BindPhoneData { phone: string; code: string }

export const platformApi = {
  getBindings: (): Promise<PlatformBinding[]> => api.get('/platforms'),

  generateQR: (platform: PlatformId): Promise<QRGenerateResponse> =>
    api.post(`/platforms/${platform}/qr/generate`),

  checkQRStatus: (platform: PlatformId, key: string): Promise<QRStatusResponse> =>
    api.get(`/platforms/${platform}/qr/status?key=${encodeURIComponent(key)}`),

  bindCookie: (platform: PlatformId, cookie: string): Promise<PlatformBinding> =>
    api.post(`/platforms/${platform}/bind/cookie?cookie=${encodeURIComponent(cookie)}`),

  sendPhoneCode: (platform: PlatformId, phone: string): Promise<void> =>
    api.post(`/platforms/${platform}/phone/code`, { phone }),

  bindPhone: (platform: PlatformId, data: BindPhoneData): Promise<PlatformBinding> =>
    api.post(`/platforms/${platform}/bind/phone`, data),

  setDefault: (platform: PlatformId): Promise<void> =>
    api.put(`/platforms/${platform}/default`),

  unbind: (platform: PlatformId): Promise<void> =>
    api.delete(`/platforms/${platform}`),
};
