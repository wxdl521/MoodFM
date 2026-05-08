import api from './client';

export const playlistApi = {
  list:            ()        => api.get('/playlists'),
  getPlaylist:     (id)      => api.get(`/playlists/${id}`),
  loved:           ()        => api.get('/songs/liked'),
  toggleLike:      (songId)  => api.post(`/songs/${songId}/like`),
  playFromPlaylist: (playlistId, startTrackIndex = 0) =>
    api.post('/radio/play-from-playlist', { playlistId, startTrackIndex }),
};
