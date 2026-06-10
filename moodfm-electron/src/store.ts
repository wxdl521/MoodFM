import type ElectronStore from 'electron-store';

export interface StoreSchema {
  windowBounds: { width: number; height: number; x?: number; y?: number };
  startMinimized: boolean;
  serverUrl: string;
}

const defaults: StoreSchema = {
  windowBounds: { width: 1200, height: 800 },
  startMinimized: false,
  serverUrl: 'http://localhost:5173',
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
