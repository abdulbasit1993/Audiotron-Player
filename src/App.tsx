import React, { useMemo } from 'react';
import { StatusBar } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import RootStack from './navigation/RootStack';
import { ThemeProvider, useTheme } from './context/ThemeContext';

const AppNavigator = () => {
  const { colors, isDark } = useTheme();

  const navigationTheme = useMemo(() => {
    const baseTheme = isDark ? DarkTheme : DefaultTheme;
    return {
      ...baseTheme,
      dark: isDark,
      colors: {
        ...baseTheme.colors,
        primary: colors.primary,
        background: colors.background,
        card: colors.card,
        text: colors.text,
        border: colors.border,
      },
    };
  }, [colors, isDark]);

  return (
    <NavigationContainer theme={navigationTheme}>
      <StatusBar
        barStyle={isDark ? 'light-content' : 'dark-content'}
        backgroundColor={colors.background}
      />
      <RootStack />
    </NavigationContainer>
  );
};

const App = () => {
  return (
    <ThemeProvider>
      <AppNavigator />
    </ThemeProvider>
  );
};

export default App;

