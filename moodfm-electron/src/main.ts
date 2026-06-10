import {
  app,
  BrowserWindow,
  ipcMain,
  globalShortcut,
  shell,
} from 'electron';
import path from 'path';
import { loadStore } from './store';
import { createTray, updateTrayState } from './tray';
import { setupTaskbar } from './taskbar';
import { checkForUpdates } from './updater';

const isDev = !app.isPackaged;

let mainWindow: BrowserWindow | null = null;
let tray: ReturnType<typeof createTray> = null;

// electron-serve is ESM-only; this will be populated before window creation.
let loadURL: ((win: BrowserWindow) => Promise<void>) | null = null;

async function createWindow() {
  const store = await loadStore();
  const bounds = store.get('windowBounds');
  const minimized = store.get('startMinimized');
  const serverUrl = store.get('serverUrl');

  mainWindow = new BrowserWindow({
    width: bounds.width,
    height: bounds.height,
    x: bounds.x,
    y: bounds.y,
    minWidth: 800,
    minHeight: 600,
    icon: path.join(__dirname, '..', 'assets', 'icon.ico'),
    show: !minimized,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      additionalArguments: [`--moodfm-server-url=${serverUrl}`],
    },
  });

  // Route external links to the system browser; block in-app navigation to outside origins.
  // Only http/https are forwarded to the OS — never file://, data:, or custom schemes.
  const openExternalIfSafe = (url: string) => {
    try {
      if (['http:', 'https:'].includes(new URL(url).protocol)) {
        shell.openExternal(url);
      }
    } catch {
      // malformed URL — drop silently
    }
  };
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternalIfSafe(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const internalPrefix = isDev ? 'http://localhost:5173' : 'app://';
    if (!url.startsWith(internalPrefix)) {
      event.preventDefault();
      openExternalIfSafe(url);
    }
  });

  // Load the Vue SPA — dev server or bundled static files.
  if (isDev) {
    await mainWindow.loadURL('http://localhost:5173');
    mainWindow.webContents.openDevTools();
  } else if (loadURL) {
    await loadURL(mainWindow);
  }

  // Persist window position and size on every change.
  const saveBounds = () => {
    if (!mainWindow || mainWindow.isMinimized() || mainWindow.isMaximized()) return;
    const { width, height, x, y } = mainWindow.getBounds();
    store.set('windowBounds', { width, height, x, y });
  };
  mainWindow.on('resize', saveBounds);
  mainWindow.on('move', saveBounds);

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  return mainWindow;
}

app.whenReady().then(async () => {
  // Dynamic import for ESM-only package (must happen before createWindow in prod).
  if (!isDev) {
    const { default: electronServe } = await import('electron-serve');
    loadURL = electronServe({ directory: 'app' });
  }

  const win = await createWindow();

  // System tray with media controls.
  tray = createTray(win);

  // Windows taskbar thumbnail buttons.
  if (process.platform === 'win32') {
    setupTaskbar(win);
  }

  // Register global media key shortcuts (work even when the app is not focused).
  const shortcuts: Record<string, string> = {
    MediaPlayPause: 'electron:player:play-pause',
    MediaNextTrack: 'electron:player:next',
    MediaPrevTrack: 'electron:player:prev',
  };

  for (const [key, channel] of Object.entries(shortcuts)) {
    const registered = globalShortcut.register(key, () => {
      if (win && !win.isDestroyed()) {
        win.webContents.send(channel);
      }
    });
    if (!registered) {
      console.warn(`Failed to register global shortcut: ${key}`);
    }
  }

  // IPC: renderer pushes player state whenever the current song or
  // play/pause status changes. Main process uses it to update the tray
  // tooltip and the window title bar.
  ipcMain.on(
    'electron:player:update',
    (
      _event,
      state: { title?: string; artist?: string; isPlaying?: boolean },
    ) => {
      updateTrayState(tray, state);

      if (win && !win.isDestroyed()) {
        const title =
          state.title && state.artist
            ? `${state.title} - ${state.artist} - MoodFM`
            : 'MoodFM';
        win.setTitle(title);
      }
    },
  );

  // Non-blocking update check.
  checkForUpdates(win);
});

// Quit when all windows are closed (standard Windows behaviour).
app.on('window-all-closed', () => {
  app.quit();
});

// Clean up global shortcuts before the process exits.
app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});
