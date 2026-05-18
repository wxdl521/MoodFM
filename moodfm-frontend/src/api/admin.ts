import api from './client'

// ── Types ────────────────────────────────────────────

export interface GlobalBlacklist {
  id: number
  type: 'artist' | 'song' | 'keyword'
  value: string
  artist?: string
  scope?: string
  reason?: string
  addedBy: string
  createdAt: string
}

export interface AdminNotification {
  id: number
  title: string
  body: string
  type: string
  targetGroup: string
  status: 'draft' | 'scheduled' | 'sent'
  scheduledAt?: string
  sentAt?: string
  sentCount: number
  openedCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface AdminAuditLog {
  id: number
  operator: string
  operation: string
  module: string
  detail?: string
  level: 'ok' | 'info' | 'warn' | 'danger'
  createdAt: string
}

// ── Blacklist ─────────────────────────────────────────

export const blacklistAdminApi = {
  list: (type?: string, q?: string): Promise<GlobalBlacklist[]> => {
    const params: Record<string, string> = {}
    if (type) params.type = type
    if (q) params.q = q
    return api.get('/admin/blacklist', { params })
  },
  add: (item: Omit<GlobalBlacklist, 'id' | 'createdAt'>): Promise<GlobalBlacklist> =>
    api.post('/admin/blacklist', item),
  remove: (id: number): Promise<void> =>
    api.delete(`/admin/blacklist/${id}`),
}

// ── Notifications ─────────────────────────────────────

export const notificationsAdminApi = {
  list: (): Promise<AdminNotification[]> =>
    api.get('/admin/notifications'),
  create: (notif: Partial<AdminNotification>): Promise<AdminNotification> =>
    api.post('/admin/notifications', notif),
  send: (id: number): Promise<void> =>
    api.post(`/admin/notifications/${id}/send`),
  remove: (id: number): Promise<void> =>
    api.delete(`/admin/notifications/${id}`),
}

// ── Config ────────────────────────────────────────────

export const configAdminApi = {
  getFlags: (): Promise<Record<string, string>> =>
    api.get('/admin/config/flags'),
  setFlag: (key: string, enabled: boolean): Promise<void> =>
    api.put(`/admin/config/flags/${key}`, { enabled }),

  getKv: (): Promise<Record<string, string>> =>
    api.get('/admin/config/kv'),
  setKv: (key: string, value: string): Promise<void> =>
    api.put(`/admin/config/kv/${key}`, { value }),
}

// ── Scene Templates ───────────────────────────────────

export interface SceneTemplate {
  id: number
  key: string
  name: string
  cn: string
  active: boolean
  songs: number
  accuracy: string
}

export const sceneAdminApi = {
  list: (): Promise<SceneTemplate[]> =>
    api.get('/admin/scenes'),
  update: (id: number, data: Partial<SceneTemplate>): Promise<void> =>
    api.put(`/admin/scenes/${id}`, data),
  create: (data: Omit<SceneTemplate, 'id'>): Promise<SceneTemplate> =>
    api.post('/admin/scenes', data),
}

// ── AI Config ─────────────────────────────────────────

export const aiConfigAdminApi = {
  getWeights: (): Promise<Record<string, string>> =>
    api.get('/admin/config/ai-weights'),
  saveWeights: (weights: Record<string, string>): Promise<void> =>
    api.put('/admin/config/ai-weights', weights),
  getPrompt: (): Promise<{ prompt: string }> =>
    api.get('/admin/config/ai-prompt'),
  savePrompt: (prompt: string): Promise<void> =>
    api.put('/admin/config/ai-prompt', { prompt }),
}

// ── Platform Stats ────────────────────────────────────

export interface PlatformStat {
  bound: number
  expiring: number
  expired: number
}

export interface CookieExpiryRow {
  userId: number
  username: string
  platform: string
  expiresAt: string   // "2026-05-18T00:00:00"
  daysLeft: number
}

export const platformAdminApi = {
  getStats: (): Promise<Record<string, PlatformStat>> =>
    api.get('/admin/platforms/stats'),
  getCookieExpiry: (): Promise<CookieExpiryRow[]> =>
    api.get('/admin/platforms/cookie-expiry'),
}

// ── Analytics ─────────────────────────────────────────

export interface GenreCount  { genre: string;    count: number }
export interface PlatformCount { platform: string; count: number }
export interface TopSong     { title: string; artist: string; playCount: number }
export interface MoodDist    { thisMonth: number[]; lastMonth: number[] }

export const analyticsAdminApi = {
  getDau:      (days: number): Promise<{ labels: string[]; dau: number[] }> =>
    api.get('/admin/dashboard/activity', { params: { days } }),
  getGenre:    (days: number): Promise<GenreCount[]> =>
    api.get('/admin/analytics/genre', { params: { days } }),
  getPlatform: (days: number): Promise<PlatformCount[]> =>
    api.get('/admin/analytics/platform', { params: { days } }),
  getTopSongs: (days: number): Promise<TopSong[]> =>
    api.get('/admin/analytics/top-songs', { params: { days } }),
  getMood:     (days: number): Promise<MoodDist> =>
    api.get('/admin/analytics/mood', { params: { days } }),
}

// ── Audit Log ─────────────────────────────────────────

export const auditLogAdminApi = {
  list: (limit = 50): Promise<AdminAuditLog[]> =>
    api.get('/admin/audit-log', { params: { limit } }),
}
