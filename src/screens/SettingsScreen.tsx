import React, { useMemo } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootStack';
import { RadioGroup, RadioOption } from '../components/RadioButton';
import { useTheme } from '../context/ThemeContext';
import { ThemeColors } from '../theme/colors';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

const SettingsScreen = ({ navigation }: Props) => {
  const { colors, themePreference, setThemePreference } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]);

  const darkModeOptions: RadioOption[] = [
    { label: 'Enable', value: 'enable' },
    { label: 'Disable', value: 'disable' },
    { label: 'System Default', value: 'system' },
  ];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Pressable
          style={styles.backButton}
          onPress={() => navigation.goBack()}
          accessibilityRole="button"
          accessibilityLabel="Back to library"
        >
          <Text style={styles.backButtonText}>← Library</Text>
        </Pressable>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Settings</Text>

        <View style={styles.optionContainer}>
          <Text style={styles.subheading}>Appearance</Text>
          <Text style={styles.label}>Dark Mode:</Text>

          <RadioGroup
            options={darkModeOptions}
            value={themePreference}
            onChange={val =>
              setThemePreference(val as 'enable' | 'disable' | 'system')
            }
            activeColor={colors.primary}
            inactiveColor={colors.radioInactive}
            size={20}
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const createStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: colors.background,
    },
    header: {
      paddingHorizontal: 20,
      paddingTop: 8,
      paddingBottom: 4,
      flexDirection: 'row',
      alignItems: 'center',
    },
    backButton: {
      paddingVertical: 8,
      paddingHorizontal: 12,
      borderRadius: 8,
      backgroundColor: colors.surface,
    },
    backButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: colors.primary,
    },
    content: {
      paddingHorizontal: 24,
      paddingTop: 18,
      paddingBottom: 24,
    },
    title: {
      fontSize: 26,
      fontWeight: '700',
      color: colors.text,
      marginBottom: 20,
    },
    optionContainer: {
      marginVertical: 12,
      backgroundColor: colors.surface,
      borderRadius: 14,
      padding: 16,
      borderWidth: 1,
      borderColor: colors.border,
    },
    subheading: {
      fontSize: 18,
      fontWeight: '700',
      color: colors.text,
      marginBottom: 8,
    },
    label: {
      fontSize: 14,
      color: colors.textSecondary,
      marginBottom: 12,
    },
  });

export default SettingsScreen;

