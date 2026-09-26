import { useCallback, useSyncExternalStore } from 'react';
import {
  getClientLocale,
  subscribeToLocale,
  translateClientMessage,
} from '@/lib/clientTranslations';

type Params = Record<string, string | number>;

/**
 * Client-side i18n using the ported JSON catalogs. Returns the key text when a
 * translation is missing so strings never silently disappear.
 *
 * Subscribes to locale changes so every mounted consumer re-renders the moment
 * the language is switched (including screens that stay mounted in a tab stack).
 */
export function useTranslate() {
  useSyncExternalStore(subscribeToLocale, getClientLocale, getClientLocale);

  return useCallback((key: string, params?: Params) => {
    return translateClientMessage(key, params) ?? key;
  }, []);
}
