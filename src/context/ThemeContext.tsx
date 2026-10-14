import React, { createContext, useContext, useState } from 'react';
import { useColorScheme } from 'react-native';
import { storage } from '../config/mmkvStorage';
import { ThemeColors, lightColors, darkColors } from '../theme/colors';

export type ThemePreference = 'enable' | 'disable' | 'system';
export type ActiveTheme = 'light' | 'dark';

export interface ThemeContextType {
  themePreference: ThemePreference;
  setThemePreference: (preference: ThemePreference) => void;
  activeTheme: ActiveTheme;
  isDark: boolean;
  colors: ThemeColors;
}

const THEME_STORAGE_KEY = 'app_dark_mode_preference';

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const systemColorScheme = useColorScheme();

  const [themePreference, setThemePreferenceState] = useState<ThemePreference>(
    () => {
      const saved = storage.getString(THEME_STORAGE_KEY);
      return (saved as ThemePreference) || 'system';
    },
  );

  const setThemePreference = (preference: ThemePreference) => {
    setThemePreferenceState(preference);
    storage.set(THEME_STORAGE_KEY, preference);
  };

  const activeTheme: ActiveTheme =
    themePreference === 'system'
      ? systemColorScheme === 'dark'
        ? 'dark'
        : 'light'
      : themePreference === 'enable'
      ? 'dark'
      : 'light';

  const isDark = activeTheme === 'dark';
  const colors = isDark ? darkColors : lightColors;

  return (
    <ThemeContext.Provider
      value={{
        themePreference,
        setThemePreference,
        activeTheme,
        isDark,
        colors,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

