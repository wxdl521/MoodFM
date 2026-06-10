import { contextBridge, ipcRenderer } from 'electron';

// main 进程通过 webPreferences.additionalArguments 注入（沙箱 preload 也能读 process.argv）
const serverUrlArg = process.argv.find((a) => a.startsWith('--moodfm-server-url='));
const serverUrl = serverUrlArg
  ? serverUrlArg.slice('--moodfm-server-url='.length)
  : 'http://localhost:5173';

contextBridge.exposeInMainWorld('__MOODFM_SERVER_URL', serverUrl);

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
