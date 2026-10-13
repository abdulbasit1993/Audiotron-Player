import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
  Alert,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Slider from '@react-native-community/slider';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/RootStack';

import AudioPlayer, { subscribeToPlaybackState } from '../native/AudioPlayer';
import type { PlaybackStateEvent } from '../../specs/NativeAudioPlayer';

type Props = NativeStackScreenProps<RootStackParamList, 'Player'>;

function formatTime(ms: number) {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

const PlayerScreen = ({ route, navigation }: Props) => {
  const { uri, name } = route.params;

  const [loading, setLoading] = useState(true);
  const [loopA, setLoopA] = useState<number | null>(null);
  const [loopB, setLoopB] = useState<number | null>(null);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);
  const [loopEnabled, setLoopEnabled] = useState(false);

  const mounted = useRef(true);
  const isSeeking = useRef(false);

  useEffect(() => {
    mounted.current = true;

    async function loadAudio() {
      setLoading(true);
      setDuration(0);
      setPosition(0);
      isSeeking.current = false;
      setLoopA(null);
      setLoopB(null);
      setLoopEnabled(false);

      try {
        await AudioPlayer.load(uri);

        const durationMs = await AudioPlayer.getDuration();
        const currentPos = await AudioPlayer.getCurrentPosition();

        if (mounted.current) {
          if (durationMs > 0) {
            setDuration(durationMs);
          }
          setPosition(currentPos || 0);
          setIsPlaying(true);
        }
      } catch (e: unknown) {
        const message =
          e instanceof Error ? e.message : 'Unable to load audio.';

        if (mounted.current) {
          Alert.alert('Playback Error', message);
        }
      } finally {
        if (mounted.current) {
          setLoading(false);
        }
      }
    }

    loadAudio();

    return () => {
      mounted.current = false;
      // Do NOT release player on unmount: allow background playback to continue
      // seamlessly when user navigates back to Home or between screens!
    };
  }, [uri]);

  useEffect(() => {
    if (loading) return;

    const unsubscribe = subscribeToPlaybackState(
      (event: PlaybackStateEvent) => {
        if (!mounted.current) return;

        const {
          position: newPosition,
          duration: newDuration,
          state,
          error,
        } = event;

        if (newDuration > 0) {
          setDuration(newDuration);
        }

        if (!isSeeking.current && newDuration > 0) {
          setPosition(Math.max(0, Math.min(newPosition, newDuration)));
        }

        if (state === 'playing') {
          setIsPlaying(true);
        } else if (state === 'paused' || state === 'stopped') {
          setIsPlaying(false);
        }

        if (state === 'completed') {
          setPosition(0);
          setIsPlaying(false);
          setLoopA(null);
          setLoopB(null);
          setLoopEnabled(false);
        } else if (state === 'error' && error) {
          Alert.alert('Playback Error', error);
        }
      },
    );

    return () => {
      unsubscribe();
    };
  }, [loading]);

  async function perform(action: () => Promise<unknown>) {
    try {
      await action();
    } catch (e) {
      const message =
        e instanceof Error ? e.message : 'Audio operation failed.';
      Alert.alert('Audio Player', message);
    }
  }

  async function markA() {
    await perform(async () => {
      const positionMs = await AudioPlayer.setLoopA();
      setLoopA(positionMs);
      setLoopB(null);
      setLoopEnabled(false);
    });
  }

  async function markB() {
    await perform(async () => {
      const positionMs = await AudioPlayer.setLoopB();
      setLoopB(positionMs);
      setLoopEnabled(true);
    });
  }

  async function clearLoop() {
    await perform(async () => {
      await AudioPlayer.clearLoop();
      setLoopA(null);
      setLoopB(null);
      setLoopEnabled(false);
    });
  }

  async function handleSeek(value: number) {
    const target = Math.round(value);

    try {
      await AudioPlayer.seekTo(target);
      if (mounted.current) {
        setPosition(target);
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : 'Unable to seek audio.';

      Alert.alert('Seek Error', message);
    } finally {
      isSeeking.current = false;
    }
  }

  if (loading) {
    return (
      <SafeAreaView style={styles.container}>
        <ActivityIndicator size="large" color="#2563EB" />
        <Text style={styles.status}>Loading audio...</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Pressable
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.backButtonText}>← Library</Text>
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.artwork}>
          <Text style={styles.musicNote}>♫</Text>
        </View>

        <Text style={styles.title} numberOfLines={2}>
          {name}
        </Text>

        <Text style={styles.subtitle}>
          {isPlaying ? 'Playing in background' : 'Paused'}
        </Text>

        {/* Draggable seek bar */}
        <View style={styles.seekContainer}>
          <Slider
            style={styles.slider}
            minimumValue={0}
            maximumValue={Math.max(duration, 1)}
            value={Math.min(position, duration)}
            minimumTrackTintColor="#2563EB"
            maximumTrackTintColor="#D4D4D8"
            thumbTintColor="#2563EB"
            disabled={duration <= 0}
            onSlidingStart={() => {
              isSeeking.current = true;
            }}
            onValueChange={val => {
              isSeeking.current = true;
              setPosition(val);
            }}
            onSlidingComplete={handleSeek}
          />

          <View style={styles.timeRow}>
            <Text style={styles.timeText}>{formatTime(position)}</Text>

            <Text style={styles.timeText}>{formatTime(duration)}</Text>
          </View>
        </View>

        <View style={styles.controls}>
          <PlayerButton
            label="▶ Play"
            onPress={() =>
              perform(async () => {
                await AudioPlayer.play();
                setIsPlaying(true);
              })
            }
          />

          <PlayerButton
            label="Ⅱ Pause"
            onPress={() =>
              perform(async () => {
                await AudioPlayer.pause();
                setIsPlaying(false);
              })
            }
          />

          <PlayerButton
            label="■ Stop"
            onPress={() =>
              perform(async () => {
                await AudioPlayer.stop();

                setPosition(0);
                setIsPlaying(false);
                setLoopA(null);
                setLoopB(null);
                setLoopEnabled(false);
              })
            }
          />
        </View>

        <View style={styles.loopCard}>
          <Text style={styles.sectionTitle}>A-B Loop</Text>

          <Text style={styles.marker}>
            A: {loopA === null ? 'Not set' : formatTime(loopA)}
          </Text>

          <Text style={styles.marker}>
            B: {loopB === null ? 'Not set' : formatTime(loopB)}
          </Text>

          <View style={styles.controls}>
            <PlayerButton label="Set A" onPress={markA} />
            <PlayerButton label="Set B" onPress={markB} />
          </View>

          <PlayerButton label="Clear Loop" onPress={clearLoop} secondary />

          <Text style={styles.loopStatus}>
            {loopEnabled ? 'Loop enabled' : 'Loop disabled'}
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

function PlayerButton({
  label,
  onPress,
  secondary = false,
}: {
  label: string;
  onPress: () => void;
  secondary?: boolean;
}) {
  return (
    <TouchableOpacity
      style={[styles.button, secondary && styles.secondaryButton]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <Text
        style={[styles.buttonText, secondary && styles.secondaryButtonText]}
      >
        {label}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
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
    backgroundColor: '#F3F4F6',
  },
  backButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#2563EB',
  },
  content: {
    paddingHorizontal: 24,
    paddingBottom: 24,
    alignItems: 'center',
  },
  artwork: {
    width: 150,
    height: 150,
    borderRadius: 24,
    backgroundColor: '#DBEAFE',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 20,
  },
  musicNote: {
    fontSize: 76,
    color: '#2563EB',
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#171717',
    textAlign: 'center',
  },
  subtitle: {
    color: '#737373',
    marginTop: 6,
    marginBottom: 16,
    fontSize: 14,
  },
  seekContainer: {
    alignSelf: 'stretch',
    marginBottom: 12,
  },
  slider: {
    width: '100%',
    height: 40,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
  },
  timeText: {
    color: '#525252',
    fontSize: 13,
    fontVariant: ['tabular-nums'],
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    flexWrap: 'wrap',
    gap: 10,
    marginVertical: 12,
  },
  button: {
    backgroundColor: '#2563EB',
    paddingVertical: 13,
    paddingHorizontal: 16,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 90,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#E5E5E5',
    alignSelf: 'stretch',
  },
  secondaryButtonText: {
    color: '#171717',
  },
  loopCard: {
    alignSelf: 'stretch',
    backgroundColor: '#F5F5F5',
    borderRadius: 16,
    padding: 18,
    marginTop: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#171717',
    marginBottom: 12,
  },
  marker: {
    color: '#404040',
    fontSize: 15,
    marginVertical: 4,
  },
  loopStatus: {
    textAlign: 'center',
    color: '#737373',
    marginTop: 12,
  },
  status: {
    textAlign: 'center',
    marginTop: 12,
    color: '#737373',
  },
});

export default PlayerScreen;
