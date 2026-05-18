import api from './client'
import type { SongVO } from '@/types'

export type SearchMode = 'keyword' | 'mood'

export interface SearchResult {
  mode: SearchMode
  query: string
  songs: SongVO[]
  notice?: string
}

export const searchApi = {
  search(q: string, mode: SearchMode = 'keyword', limit = 20): Promise<SearchResult> {
    return api.get('/search', { params: { q, mode, limit } })
  },
}
