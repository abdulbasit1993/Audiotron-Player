import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import HomeScreen from '../screens/HomeScreen';
import PlayerScreen from '../screens/PlayerScreen';
import SettingsScreen from '../screens/SettingsScreen';

export type RootStackParamList = {
  Home: undefined;
  Player: {
    uri: string;
    name: string;
  };
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function RootStack() {
  return (
    <Stack.Navigator
      initialRouteName="Home"
      screenOptions={{ headerShown: false }}
    >
      <Stack.Screen name="Home" component={HomeScreen} />
      <Stack.Screen name="Player" component={PlayerScreen} />
      <Stack.Screen name="Settings" component={SettingsScreen} />
    </Stack.Navigator>
  );
}
