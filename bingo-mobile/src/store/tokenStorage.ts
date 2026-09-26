import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

const JWT_KEY = 'bingo.jwt';

interface Storage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

const memory: Record<string, string> = {};
const memoryStorage: Storage = {
  async getItem(key) {
    return memory[key] ?? null;
  },
  async setItem(key, value) {
    memory[key] = value;
  },
  async removeItem(key) {
    delete memory[key];
  },
};

function loadAsyncStorage(): Storage | null {
  try {
    const self = AsyncStorage as unknown as Storage;
    const mod = AsyncStorage as unknown as { default?: Storage };
    const impl =
      typeof mod.default?.getItem === 'function' &&
      typeof mod.default?.setItem === 'function' &&
      typeof mod.default?.removeItem === 'function'
        ? mod.default
        : self;
    if (
      typeof impl.getItem === 'function' &&
      typeof impl.setItem === 'function' &&
      typeof impl.removeItem === 'function'
    ) {
      return impl;
    }
  } catch {
    // ignore
  }
  return null;
}

let secureStore: Storage | null = null;
try {
  // Load lazily so a broken/missing native module never crashes app startup.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const SecureStore = require('expo-secure-store') as {
    getItemAsync?: Storage['getItem'];
    setItemAsync?: Storage['setItem'];
    deleteItemAsync?: Storage['removeItem'];
  } | null;
  if (
    SecureStore &&
    typeof SecureStore.setItemAsync === 'function' &&
    typeof SecureStore.getItemAsync === 'function' &&
    typeof SecureStore.deleteItemAsync === 'function'
  ) {
    secureStore = {
      getItem: SecureStore.getItemAsync,
      setItem: SecureStore.setItemAsync,
      removeItem: SecureStore.deleteItemAsync,
    };
  }
} catch {
  secureStore = null;
}

function candidateStorages(): Storage[] {
  const asyncStore = loadAsyncStorage();
  if (Platform.OS === 'web') {
    return [asyncStore, memoryStorage].filter((s): s is Storage => s !== null);
  }
  return [secureStore, asyncStore, memoryStorage].filter((s): s is Storage => s !== null);
}

/**
 * Persists the JWT. Prefers SecureStore (Keychain/Keystore) on native, falls
 * back to AsyncStorage, and finally to in-memory storage. Every call is
 * guarded so no storage failure can throw or break authentication.
 */
export const tokenStorage = {
  async get(): Promise<string | null> {
    for (const storage of candidateStorages()) {
      try {
        const value = await storage.getItem(JWT_KEY);
        if (value != null) {
          return value;
        }
      } catch {
        // try the next backend
      }
    }
    return null;
  },
  async set(token: string): Promise<void> {
    for (const storage of candidateStorages()) {
      try {
        await storage.setItem(JWT_KEY, token);
        return;
      } catch {
        // try the next backend
      }
    }
  },
  async clear(): Promise<void> {
    for (const storage of candidateStorages()) {
      try {
        await storage.removeItem(JWT_KEY);
      } catch {
        // best effort
      }
    }
  },
};