import api from './client';

export const playlistApi = {
  getPlaylist: (id) => api.get(`/playlists/${id}`),

  playFromPlaylist: (playlistId, startTrackIndex = 0) =>
    api.post('/radio/play-from-playlist', { playlistId, startTrackIndex }),
};
