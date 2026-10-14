export interface ThemeColors {
  // Base backgrounds
  background: string;
  surface: string;
  surfaceSubtle: string;
  card: string;
  cardBorder: string;

  // Primary & Accents
  primary: string;
  primaryLight: string;
  primaryDark: string;
  primaryText: string;

  // Typography
  text: string;
  textSecondary: string;
  textMuted: string;
  textPlaceholder: string;

  // Borders & Dividers
  border: string;
  borderSubtle: string;

  // Inputs
  inputBackground: string;
  inputBorder: string;
  inputText: string;

  // Interactive / Buttons
  secondaryButton: string;
  secondaryButtonText: string;

  // Slider & Media Controls
  sliderMinTrack: string;
  sliderMaxTrack: string;
  sliderThumb: string;
  iconBackground: string;
  iconTint: string;

  // Status & Feedback
  error: string;
  radioInactive: string;
  radioDisabled: string;
}

export const lightColors: ThemeColors = {
  background: '#FFFFFF',
  surface: '#F3F4F6',
  surfaceSubtle: '#F9FAFB',
  card: '#F5F5F5',
  cardBorder: '#E5E7EB',

  primary: '#2563EB',
  primaryLight: '#DBEAFE',
  primaryDark: '#1D4ED8',
  primaryText: '#FFFFFF',

  text: '#171717',
  textSecondary: '#525252',
  textMuted: '#737373',
  textPlaceholder: '#A3A3A3',

  border: '#E5E7EB',
  borderSubtle: '#D4D4D4',

  inputBackground: '#FAFAFA',
  inputBorder: '#D4D4D4',
  inputText: '#171717',

  secondaryButton: '#E5E5E5',
  secondaryButtonText: '#171717',

  sliderMinTrack: '#2563EB',
  sliderMaxTrack: '#D4D4D8',
  sliderThumb: '#2563EB',
  iconBackground: '#DBEAFE',
  iconTint: '#2563EB',

  error: '#DC2626',
  radioInactive: '#CCCCCC',
  radioDisabled: '#E0E0E0',
};

export const darkColors: ThemeColors = {
  background: '#121212',
  surface: '#1E1E1E',
  surfaceSubtle: '#181818',
  card: '#1E1E1E',
  cardBorder: '#2E2E2E',

  primary: '#3B82F6',
  primaryLight: '#1E293B',
  primaryDark: '#2563EB',
  primaryText: '#FFFFFF',

  text: '#F9FAFB',
  textSecondary: '#9CA3AF',
  textMuted: '#6B7280',
  textPlaceholder: '#6B7280',

  border: '#2E2E2E',
  borderSubtle: '#262626',

  inputBackground: '#1E1E1E',
  inputBorder: '#374151',
  inputText: '#F9FAFB',

  secondaryButton: '#2E2E2E',
  secondaryButtonText: '#F3F4F6',

  sliderMinTrack: '#3B82F6',
  sliderMaxTrack: '#374151',
  sliderThumb: '#3B82F6',
  iconBackground: '#1E293B',
  iconTint: '#60A5FA',

  error: '#EF4444',
  radioInactive: '#52525B',
  radioDisabled: '#3F3F46',
};
