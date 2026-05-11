import api from './client';

interface Song { id: string; title: string; artist: string; album?: string; platform: 'netease' | 'qqmusic'; platformSongId: string; duration: number; coverUrl?: string; audioUrl?: string }
interface Playlist { id: string; name: string; platform: 'netease' | 'qqmusic'; coverUrl?: string; trackCount: number; description?: string }

interface PlaylistDetail extends Playlist { tracks: Song[] }
interface PlayFromPlaylistResponse { sessionId: string }

export interface SmartPlaylistSong { title: string; artist: string; playCount: number }
export interface SmartPlaylistSummary { type: string; name: string; icon: string; songCount: number; songs: SmartPlaylistSong[] }
export interface SmartPlaylistDetail { type: string; name: string; icon: string; songCount: number; songs: SmartPlaylistSong[] }

export const playlistApi = {
  list:             (): Promise<Playlist[]>                => api.get('/playlists'),
  getPlaylist:      (id: string): Promise<PlaylistDetail>  => api.get(`/playlists/${id}`),
  loved:            (): Promise<Song[]>                    => api.get('/songs/liked'),
  toggleLike:       (songId: string): Promise<{ liked: boolean }> => api.post(`/songs/${songId}/like`),
  playFromPlaylist: (playlistId: string, startTrackIndex = 0): Promise<PlayFromPlaylistResponse> =>
    api.post('/radio/play-from-playlist', { playlistId, startTrackIndex }),

  // Smart playlists
  listSmart:        (): Promise<SmartPlaylistSummary[]>    => api.get('/playlists/smart'),
  getSmart:         (type: string): Promise<SmartPlaylistDetail> => api.get(`/playlists/smart/${type}`),
};
