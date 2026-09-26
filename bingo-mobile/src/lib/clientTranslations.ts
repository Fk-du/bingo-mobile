import en from '@/i18n/messages/en.json';
import am from '@/i18n/messages/am.json';

let currentLocale: 'en' | 'am' = 'en';
const localeListeners = new Set<() => void>();

const catalogs = { en, am } as const;

export function setClientLocale(locale: string) {
  const next: 'en' | 'am' = locale === 'am' ? 'am' : 'en';
  if (next === currentLocale) return;
  currentLocale = next;
  for (const listener of Array.from(localeListeners)) {
    listener();
  }
}

export function getClientLocale(): 'en' | 'am' {
  return currentLocale;
}

export function subscribeToLocale(listener: () => void): () => void {
  localeListeners.add(listener);
  return () => {
    localeListeners.delete(listener);
  };
}

type Params = Record<string, string | number>;

function resolvePlural(template: string, params: Params): string {
  const top = template.match(/^\{\s*([A-Za-z0-9_]+)\s*,\s*plural\s*,\s*(.*)\}\s*$/s);
  if (!top) return template;
  const param = top[1];
  const count = Number(params[param]);
  const branchRe = /(\w+)\s*\{([^{}]*(?:\{[^{}]*\}[^{}]*)*)\}/g;
  let chosen: string | null = null;
  let other: string | null = null;
  for (const m of top[2].matchAll(branchRe)) {
    const rule = m[1];
    if (rule === 'other') other = m[2];
    if (rule === 'one' && count === 1) chosen = m[2];
  }
  const out = chosen ?? other ?? '';
  return out.split('#').join(String(count));
}

export function translateClientMessage(
  key: string,
  params: Params = {}
): string | null {
  const messages = catalogs[currentLocale] as Record<string, unknown>;
  let value: unknown = messages;
  for (const part of key.split('.')) {
    if (
      value &&
      typeof value === 'object' &&
      Object.prototype.hasOwnProperty.call(value, part)
    ) {
      value = (value as Record<string, unknown>)[part];
    } else {
      return null;
    }
  }
  if (typeof value !== 'string') return null;
  let out = resolvePlural(value, params);
  for (const [k, v] of Object.entries(params)) {
    out = out.split(`{${k}}`).join(String(v));
  }
  return out;
}