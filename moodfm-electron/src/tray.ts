import { Tray, Menu, nativeImage, BrowserWindow, app } from 'electron';
import path from 'path';

interface PlayerState {
  title?: string;
  artist?: string;
  isPlaying?: boolean;
}

// Module-level references so buildContextMenu can reach both.
let currentTray: Tray | null = null;
let trayWindow: BrowserWindow | null = null;

function buildContextMenu(state?: PlayerState): Menu {
  const songLabel = state?.title
    ? `${state.title}${state?.artist ? ' - ' + state.artist : ''}`
    : 'No song playing';
  const playPauseLabel = state?.isPlaying ? 'Pause' : 'Play';

  return Menu.buildFromTemplate([
    { label: songLabel, enabled: false },
    { type: 'separator' },
    {
      label: 'Show Window',
      click: () => {
        trayWindow?.show();
        trayWindow?.focus();
      },
    },
    {
      label: playPauseLabel,
      click: () => {
        trayWindow?.webContents.send('electron:player:play-pause');
      },
    },
    {
      label: 'Next',
      click: () => {
        trayWindow?.webContents.send('electron:player:next');
      },
    },
    {
      label: 'Previous',
      click: () => {
        trayWindow?.webContents.send('electron:player:prev');
      },
    },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        currentTray?.destroy();
        app.quit();
      },
    },
  ]);
}

export function createTray(window: BrowserWindow): Tray | null {
  trayWindow = window;

  // Try the dedicated tray icon first, fall back to the main app icon.
  const trayIconPath = path.join(__dirname, '..', 'assets', 'tray-icon.ico');
  const appIconPath = path.join(__dirname, '..', 'assets', 'icon.ico');

  try {
    const icon = nativeImage.createFromPath(trayIconPath);
    if (icon.isEmpty()) throw new Error('empty');
    currentTray = new Tray(icon);
  } catch {
    try {
      const fallback = nativeImage.createFromPath(appIconPath);
      if (fallback.isEmpty()) throw new Error('empty');
      currentTray = new Tray(fallback);
    } catch {
      console.error('Failed to create tray: no valid icon file found in assets/');
      return null;
    }
  }

  currentTray.setToolTip('MoodFM');
  currentTray.setContextMenu(buildContextMenu());

  // Double-click the tray icon to show and focus the main window.
  currentTray.on('double-click', () => {
    window.show();
    window.focus();
  });

  return currentTray;
}

export function updateTrayState(
  tray: Tray | null,
  state: PlayerState,
): void {
  if (!tray || tray.isDestroyed()) return;

  // Update tooltip with current song info and playback status.
  if (state.title) {
    const songText = state.artist
      ? `${state.title} - ${state.artist}`
      : state.title;
    const statusText = state.isPlaying ? ' (Playing)' : ' (Paused)';
    tray.setToolTip(songText + statusText);
  } else {
    tray.setToolTip('MoodFM');
  }

  // Rebuild the context menu so the song label and play/pause toggle stay in sync.
  tray.setContextMenu(buildContextMenu(state));
}
