import type ElectronStore from 'electron-store';

export interface StoreSchema {
  windowBounds: { width: number; height: number; x?: number; y?: number };
  startMinimized: boolean;
  serverUrl: string;
}

// ⚠️ 决策点：打包发布前必须把 PROD_SERVER_URL 改成真实服务器地址（如 http://你的服务器IP）
const PROD_SERVER_URL = 'http://YOUR_SERVER_IP';

const defaults: StoreSchema = {
  windowBounds: { width: 1200, height: 800 },
  startMinimized: false,
  serverUrl:
    process.env.NODE_ENV === 'development'
      ? 'http://localhost:5173'
      : PROD_SERVER_URL,
};

let _store: InstanceType<typeof ElectronStore<StoreSchema>> | null = null;

export async function loadStore(): Promise<
  InstanceType<typeof ElectronStore<StoreSchema>>
> {
  if (_store) return _store;
  // electron-store v8 is ESM-only; dynamic import works in CommonJS modules
  const { default: Store } = await import('electron-store');
  _store = new Store<StoreSchema>({ defaults });
  return _store;
}
