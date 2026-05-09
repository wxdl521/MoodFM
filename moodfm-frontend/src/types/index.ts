export type Platform = 'netease' | 'qqmusic'
export type MoodPreset = 'dusk' | 'melancholy' | 'energetic' | 'focused' | 'calm'
export type Theme = 'light' | 'dark'
export type FeedbackAction = 'like' | 'dislike' | 'skip' | 'love' | 'blacklist'
export type SessionStatus = 'active' | 'paused' | 'ended'

export interface User {
  id: number
  username: string
  email: string
  avatarUrl?: string
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
}

export interface RadioSession {
  sessionId: string
  moodText: string
  scene?: string
  status: SessionStatus
  createdAt: string
  moodPreset?: MoodPreset
}

export interface Playlist {
  id: string
  name: string
  platform: Platform
  coverUrl?: string
  trackCount: number
  description?: string
  isLoved?: boolean
}

export interface PlayHistory {
  id: string
  song: Song
  playedAt: string
  sessionId?: string
  durationPlayed: number
}

export interface Feedback {
  songId: string
  sessionId: string
  action: FeedbackAction
  timestamp?: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface ApiError {
  message: string
  code?: string
  status?: number
}
