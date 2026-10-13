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

type SortOrder = 'asc' | 'desc';
type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Home'>;

const HomeScreen = ({ navigation }: { navigation: NavigationProp }) => {
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
      <Text style={styles.title}>Audio Library</Text>

      <Pressable
        style={styles.button}
        onPress={selectFolder}
        disabled={loading}
      >
        <Text style={styles.buttonText}>
          {folderUri ? 'Choose Another Folder' : 'Browse Folders'}
        </Text>
      </Pressable>

      {loading && <ActivityIndicator style={styles.loader} />}

      {!!error && <Text style={styles.error}>{error}</Text>}

      {!loading && folderUri !== null && (
        <>
          {/* Search Input */}
          <TextInput
            style={styles.searchInput}
            placeholder="Search by name..."
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#A3A3A3"
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
              <Text>♫</Text>
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 20,
    color: '#171717',
  },
  button: {
    backgroundColor: '#2563EB',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
    marginBottom: 20,
  },
  buttonText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  loader: {
    marginVertical: 20,
  },
  error: {
    color: '#DC2626',
    marginBottom: 12,
  },
  empty: {
    color: '#737373',
    marginVertical: 20,
  },
  searchInput: {
    borderWidth: 1,
    borderColor: '#D4D4D4',
    borderRadius: 10,
    padding: 14,
    fontSize: 16,
    color: '#171717',
    backgroundColor: '#FAFAFA',
    marginBottom: 12,
  },
  sortButton: {
    backgroundColor: '#F5F5F5',
    borderWidth: 1,
    borderColor: '#D4D4D4',
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
    color: '#404040',
  },
  sortArrow: {
    fontSize: 17,
    fontWeight: '700',
    color: '#2563EB',
  },
  file: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#D4D4D4',
  },
  icon: {
    width: 42,
    height: 42,
    borderRadius: 10,
    backgroundColor: '#DBEAFE',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fileInfo: {
    flex: 1,
    marginHorizontal: 12,
  },
  fileName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#171717',
  },
  fileSize: {
    color: '#737373',
    fontSize: 12,
    marginTop: 4,
  },
  play: {
    fontSize: 18,
    color: '#2563EB',
  },
});

export default HomeScreen;
