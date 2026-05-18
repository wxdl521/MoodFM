export type Platform = 'netease' | 'qqmusic'
export type MoodPreset = 'dusk' | 'melancholy' | 'energetic' | 'focused' | 'calm'
export type Theme = 'light' | 'dark'
export type FeedbackAction = 'like' | 'dislike' | 'skip' | 'love' | 'blacklist'

export interface User {
  id: number
  username: string
  email: string
  avatarUrl?: string
  role?: string
}

export interface UserPreferences {
  theme: Theme
  defaultPlatform: Platform
  autoPlay: boolean
  crossfadeDuration: number
}

export interface Song {
  id: string
  title: string
  artist: string
  album?: string
  platform: Platform
  platformSongId: string
  duration: number
  coverUrl?: string
  audioUrl?: string
  bpm?: number
  tags?: string[]
  recommendReason?: string
  urlSource?: string // "qqmusic" | "netease_fallback"
}

export interface RadioSession {
  sessionId: string
  moodText: string
  scene?: string
  status: 'active' | 'paused' | 'ended'
  createdAt: string
  moodPreset?: MoodPreset
}

export interface Feedback {
  songId: string
  sessionId: string
  action: FeedbackAction
  timestamp?: string
}

export interface SongVO {
  id: number
  title: string
  artist: string
  album?: string
  durationSeconds?: number
  coverUrl?: string
  platform?: string
  platformSongId?: string
  playUrl?: string
  recommendReason?: string
  urlSource?: string // "qqmusic" | "netease_fallback"
}
