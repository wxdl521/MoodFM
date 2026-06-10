import { contextBridge, ipcRenderer } from 'electron';

// Expose the backend server URL so the Vue SPA can make API requests.
// In dev this defaults to the Vite dev server; in prod the actual server URL.
contextBridge.exposeInMainWorld(
  '__MOODFM_SERVER_URL',
  process.env.VITE_SERVER_URL || 'http://localhost:5173',
);

// Bidirectional IPC bridge for media player controls.
// Renderer calls these methods; main process listens via ipcMain.
contextBridge.exposeInMainWorld('electronAPI', {
  playerUpdate(state: { title: string; artist: string; isPlaying: boolean }) {
    ipcRenderer.send('electron:player:update', state);
  },
  onPlayPause(cb: () => void) {
    ipcRenderer.on('electron:player:play-pause', () => cb());
  },
  onNext(cb: () => void) {
    ipcRenderer.on('electron:player:next', () => cb());
  },
  onPrev(cb: () => void) {
    ipcRenderer.on('electron:player:prev', () => cb());
  },
});
