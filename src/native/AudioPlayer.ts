import { NativeEventEmitter, NativeModules } from 'react-native';
import type { PlaybackStateEvent } from '../../specs/NativeAudioPlayer';

const { NativeAudioPlayer } = NativeModules;

// Create event emitter for the native module
export const audioPlayerEmitter = new NativeEventEmitter(NativeAudioPlayer);

/**
 * Subscribe to playback state changes.
 * Replaces the 500ms polling in PlayerScreen.
 *
 * @param callback Receives PlaybackStateEvent on every native emission
 * @returns Cleanup function to unsubscribe
 */
export function subscribeToPlaybackState(
  callback: (event: PlaybackStateEvent) => void,
): () => void {
  const subscription = audioPlayerEmitter.addListener(
    'playbackStateChange',
    callback,
  );

  return () => {
    subscription.remove();
  };
}

/**
 * One-time helper to get current position (still available for imperative use)
 */
export async function getCurrentPosition(): Promise<number> {
  return NativeAudioPlayer.getCurrentPosition();
}

export default NativeAudioPlayer;
