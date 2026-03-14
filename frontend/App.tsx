import React, { useState } from 'react';
import { SafeAreaView, StatusBar, StyleSheet } from 'react-native';
import HomeScreen from './src/screens/HomeScreen';
import PlaylistPreviewScreen from './src/screens/PlaylistPreviewScreen';
import { PlaylistVariant } from './src/types';

export default function App(): JSX.Element {
  const [variants, setVariants] = useState<PlaylistVariant[] | null>(null);

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" />
      {variants ? (
        <PlaylistPreviewScreen variants={variants} onBack={() => setVariants(null)} />
      ) : (
        <HomeScreen onVariantsGenerated={setVariants} />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0a0a0a'
  }
});
