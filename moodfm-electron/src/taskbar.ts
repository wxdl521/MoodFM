import { BrowserWindow, nativeImage } from 'electron';
import path from 'path';

export function setupTaskbar(window: BrowserWindow): void {
  const iconPath = path.join(__dirname, '..', 'assets', 'icon.ico');
  const icon = nativeImage.createFromPath(iconPath);

  // Windows taskbar thumbnail toolbar: three media-control buttons that
  // appear in the taskbar preview area when the app is running.
  window.setThumbarButtons([
    {
      tooltip: 'Previous',
      icon,
      click: () => {
        window.webContents.send('electron:player:prev');
      },
    },
    {
      tooltip: 'Play / Pause',
      icon,
      click: () => {
        window.webContents.send('electron:player:play-pause');
      },
    },
    {
      tooltip: 'Next',
      icon,
      click: () => {
        window.webContents.send('electron:player:next');
      },
    },
  ]);
}
