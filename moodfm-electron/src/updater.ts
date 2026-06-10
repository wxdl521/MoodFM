import { BrowserWindow, dialog, shell, app } from 'electron';
import http from 'http';
import https from 'https';
import { loadStore } from './store';

function httpGet(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const transport = url.startsWith('https') ? https : http;
    transport
      .get(url, (res) => {
        // Follow 3xx redirects.
        if (
          res.statusCode &&
          res.statusCode >= 300 &&
          res.statusCode < 400 &&
          res.headers.location
        ) {
          httpGet(res.headers.location).then(resolve).catch(reject);
          return;
        }

        let data = '';
        res.on('data', (chunk: Buffer) => {
          data += chunk.toString();
        });
        res.on('end', () => resolve(data));
      })
      .on('error', reject);
  });
}

function isNewerVersion(remote: string, current: string): boolean {
  const r = remote.split('.').map(Number);
  const c = current.split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    if ((r[i] || 0) > (c[i] || 0)) return true;
    if ((r[i] || 0) < (c[i] || 0)) return false;
  }
  return false;
}

export async function checkForUpdates(window: BrowserWindow): Promise<void> {
  // Give the app time to settle before making a network request.
  await new Promise<void>((resolve) => setTimeout(resolve, 10_000));

  if (window.isDestroyed()) return;

  try {
    const store = await loadStore();
    const serverUrl: string = store.get('serverUrl');
    const url = `${serverUrl}/updates/latest.json`;

    const response = await httpGet(url);
    const data = JSON.parse(response);

    if (!data.version || !data.downloadUrl) return;

    const currentVersion = app.getVersion();
    if (!isNewerVersion(String(data.version), currentVersion)) return;

    const result = await dialog.showMessageBox(window, {
      type: 'info',
      title: 'Update Available',
      message: `MoodFM ${data.version} is available (current: ${currentVersion})`,
      detail: data.releaseNotes || 'A new version of MoodFM is available.',
      buttons: ['Download', 'Later'],
      defaultId: 0,
      cancelId: 1,
    });

    if (result.response === 0) {
      shell.openExternal(data.downloadUrl);
    }
  } catch {
    // Silently swallow update-check failures — the server may be unreachable
    // or the response may be malformed. Neither should crash the app.
  }
}
