import AsyncStorage from '@react-native-async-storage/async-storage';
import { colorScheme } from 'nativewind';
import { ReactNode, createContext, useCallback, useContext, useEffect, useState } from 'react';
import { Platform } from 'react-native';

export type Theme = 'dark' | 'light';

export type ThemeColors = {
  background: string;
  bg: string;
  surface: string;
  surfaceAlt: string;
  elevated: string;
  borderInactive: string;
  borderActive: string;
  primary: string;
  primaryDisabled: string;
  secondary: string;
  accent: string;
  gold: string;
  warning: string;
  success: string;
  danger: string;
  textPrimary: string;
  textSecondary: string;
  textInactive: string;
};

export const PALETTES: Record<Theme, ThemeColors> = {
  dark: {
    background: '#0B0D17',
    bg: '#0b0e1b',
    surface: '#131729',
    surfaceAlt: '#1A2038',
    elevated: '#1c2240',
    borderInactive: '#2A3252',
    borderActive: '#6B5BFF',
    primary: '#6B5BFF',
    primaryDisabled: '#3A357A',
    secondary: '#FFB454',
    accent: '#36E4B5',
    gold: '#F2C94C',
    warning: '#f2994a',
    success: '#27ae60',
    danger: '#FF5C6C',
    textPrimary: '#E7E9F5',
    textSecondary: '#9AA2C2',
    textInactive: '#6B7299',
  },
  light: {
    background: '#F4F5F9',
    bg: '#FFFFFF',
    surface: '#FFFFFF',
    surfaceAlt: '#EDF0F7',
    elevated: '#FFFFFF',
    borderInactive: '#D9DEEA',
    borderActive: '#6B5BFF',
    primary: '#6B5BFF',
    primaryDisabled: '#3A357A',
    secondary: '#FFB454',
    accent: '#36E4B5',
    gold: '#F2C94C',
    warning: '#f2994a',
    success: '#27ae60',
    danger: '#FF5C6C',
    textPrimary: '#1A1E2C',
    textSecondary: '#5B6478',
    textInactive: '#97A1B6',
  },
};

const STORAGE_KEY = 'bingo-theme';

type ThemeContextValue = {
  theme: Theme;
  colors: ThemeColors;
  isDark: boolean;
  toggle: () => void;
  setTheme: (t: Theme) => void;
};

const ThemeContext = createContext<ThemeContextValue>({
  theme: 'dark',
  colors: PALETTES.dark,
  isDark: true,
  toggle: () => {},
  setTheme: () => {},
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>('dark');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let mounted = true;
    (async () => {
      let stored: string | null = null;
      try {
        stored = await AsyncStorage.getItem(STORAGE_KEY);
      } catch {
        // Ignore storage errors; fall back to dark
      }
      if (mounted && (stored === 'light' || stored === 'dark')) setTheme(stored);
      if (mounted) setReady(true);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!ready) return;
    // On web, colorScheme.set toggles the `dark` class on <html> (harmless,
    // helps media queries). On native, calling Appearance.setColorScheme can
    // recreate the Android activity and remount the whole React tree, which
    // can drop the navigation context - so skip it; the root View's `dark`
    // class already drives the CSS variables there.
    if (Platform.OS === 'web') colorScheme.set(theme);
    void AsyncStorage.setItem(STORAGE_KEY, theme);
  }, [theme, ready]);

  const toggle = useCallback(() => setTheme((p) => (p === 'dark' ? 'light' : 'dark')), []);
  const setAndPersist = useCallback((t: Theme) => setTheme(t), []);

  return (
    <ThemeContext.Provider
      value={{ theme, colors: PALETTES[theme], isDark: theme === 'dark', toggle, setTheme: setAndPersist }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}