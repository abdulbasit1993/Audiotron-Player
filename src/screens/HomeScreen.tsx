import React, { useState, useMemo } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  TextInput,
  ActivityIndicator,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import AudioLibrary from '../native/AudioLibrary';
import type { AudioFile } from '../../specs/NativeAudioLibrary';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootStack';
import { useTheme } from '../context/ThemeContext';
import { ThemeColors } from '../theme/colors';

type SortOrder = 'asc' | 'desc';
type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Home'>;

const HomeScreen = ({ navigation }: { navigation: NavigationProp }) => {
  const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]);

  const [files, setFiles] = useState<AudioFile[]>([]);
  const [folderUri, setFolderUri] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

  async function selectFolder() {
    try {
      setLoading(true);
      setError('');

      const uri = await AudioLibrary.pickAudioFolder();
      setFolderUri(uri);

      const audioFiles = await AudioLibrary.listAudioFiles(uri);
      setFiles(audioFiles);
      setSearchQuery('');
      setSortOrder('asc');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to load audio files.');
    } finally {
      setLoading(false);
    }
  }

  const filteredAndSortedFiles = useMemo(() => {
    let result = files;

    // Filter by name (case-insensitive)
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase().trim();
      result = result.filter(file => file.name.toLowerCase().includes(query));
    }

    // Sort by name
    result = [...result].sort((a, b) => {
      const nameA = a.name.toLowerCase();
      const nameB = b.name.toLowerCase();
      return sortOrder === 'asc'
        ? nameA.localeCompare(nameB)
        : nameB.localeCompare(nameA);
    });

    return result;
  }, [files, searchQuery, sortOrder]);

  function toggleSortOrder() {
    setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'));
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Audio Library</Text>
        <TouchableOpacity
          style={styles.settingsButton}
          onPress={() => navigation.navigate('Settings')}
          accessibilityRole="button"
          accessibilityLabel="Settings"
          accessibilityHint="Navigates to application settings"
          activeOpacity={0.7}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Text style={styles.settingsIcon}>⚙</Text>
        </TouchableOpacity>
      </View>

      <Pressable
        style={styles.button}
        onPress={selectFolder}
        disabled={loading}
      >
        <Text style={styles.buttonText}>
          {folderUri ? 'Choose Another Folder' : 'Browse Folders'}
        </Text>
      </Pressable>

      {loading && (
        <ActivityIndicator
          style={styles.loader}
          size="large"
          color={colors.primary}
        />
      )}

      {!!error && <Text style={styles.error}>{error}</Text>}

      {!loading && folderUri !== null && (
        <>
          {/* Search Input */}
          <TextInput
            style={styles.searchInput}
            placeholder="Search by name..."
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor={colors.textPlaceholder}
            autoCapitalize="none"
            autoCorrect={false}
          />

          {/* Sort Toggle */}
          <TouchableOpacity style={styles.sortButton} onPress={toggleSortOrder}>
            <View style={styles.sortButtonContent}>
              <Text style={styles.sortButtonText}>
                Sort: {sortOrder === 'asc' ? 'A → Z' : 'Z → A'}
              </Text>
              <Text style={styles.sortArrow}>
                {sortOrder === 'asc' ? '↑' : '↓'}
              </Text>
            </View>
          </TouchableOpacity>
        </>
      )}

      {!loading &&
        folderUri !== null &&
        filteredAndSortedFiles.length === 0 &&
        !error && (
          <Text style={styles.empty}>
            {searchQuery
              ? `No audio files matching "${searchQuery}"`
              : 'No audio files found in this folder.'}
          </Text>
        )}

      <FlatList
        data={filteredAndSortedFiles}
        keyExtractor={item => item.uri}
        renderItem={({ item }) => (
          <Pressable
            style={styles.file}
            onPress={() =>
              navigation.navigate('Player', {
                uri: item.uri,
                name: item.name,
              })
            }
          >
            <View style={styles.icon}>
              <Text style={styles.iconText}>♫</Text>
            </View>

            <View style={styles.fileInfo}>
              <Text style={styles.fileName} numberOfLines={1}>
                {item.name}
              </Text>
              <Text style={styles.fileSize}>
                {(item.size / (1024 * 1024)).toFixed(2)} MB
              </Text>
            </View>

            <Text style={styles.play}>▶</Text>
          </Pressable>
        )}
      />
    </SafeAreaView>
  );
};

const createStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    container: {
      flex: 1,
      padding: 20,
      backgroundColor: colors.background,
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: 20,
    },
    title: {
      fontSize: 26,
      fontWeight: '700',
      color: colors.text,
    },
    settingsButton: {
      width: 42,
      height: 42,
      borderRadius: 10,
      backgroundColor: colors.surface,
      borderWidth: 1,
      borderColor: colors.border,
      alignItems: 'center',
      justifyContent: 'center',
    },
    settingsIcon: {
      fontSize: 20,
      color: colors.textSecondary,
    },
    button: {
      backgroundColor: colors.primary,
      padding: 15,
      borderRadius: 10,
      alignItems: 'center',
      marginBottom: 20,
    },
    buttonText: {
      color: colors.primaryText,
      fontWeight: '600',
    },
    loader: {
      marginVertical: 20,
    },
    error: {
      color: colors.error,
      marginBottom: 12,
    },
    empty: {
      color: colors.textMuted,
      marginVertical: 20,
    },
    searchInput: {
      borderWidth: 1,
      borderColor: colors.inputBorder,
      borderRadius: 10,
      padding: 14,
      fontSize: 16,
      color: colors.inputText,
      backgroundColor: colors.inputBackground,
      marginBottom: 12,
    },
    sortButton: {
      backgroundColor: colors.surface,
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: 10,
      padding: 12,
      marginBottom: 16,
      alignItems: 'center',
    },
    sortButtonContent: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    sortButtonText: {
      fontSize: 15,
      fontWeight: '600',
      color: colors.text,
    },
    sortArrow: {
      fontSize: 17,
      fontWeight: '700',
      color: colors.primary,
    },
    file: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 14,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.borderSubtle,
    },
    icon: {
      width: 42,
      height: 42,
      borderRadius: 10,
      backgroundColor: colors.iconBackground,
      alignItems: 'center',
      justifyContent: 'center',
    },
    iconText: {
      fontSize: 18,
      color: colors.iconTint,
    },
    fileInfo: {
      flex: 1,
      marginHorizontal: 12,
    },
    fileName: {
      fontSize: 15,
      fontWeight: '600',
      color: colors.text,
    },
    fileSize: {
      color: colors.textMuted,
      fontSize: 12,
      marginTop: 4,
    },
    play: {
      fontSize: 18,
      color: colors.primary,
    },
  });

export default HomeScreen;

