import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type AudioFile = {
  name: string;
  uri: string;
  mimeType: string;
  size: number;
};

export interface Spec extends TurboModule {
  pickAudioFolder(): Promise<string>;
  listAudioFiles(treeUri: string): Promise<Array<AudioFile>>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeAudioLibrary');
