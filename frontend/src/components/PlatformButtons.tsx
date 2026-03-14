import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { PlatformType } from '../types';

const platforms: PlatformType[] = ['spotify', 'youtube', 'soundcloud', 'deezer'];

interface Props {
  selected: PlatformType;
  onSelect: (platform: PlatformType) => void;
}

export default function PlatformButtons({ selected, onSelect }: Props): JSX.Element {
  return (
    <View style={styles.container}>
      {platforms.map((platform) => (
        <Pressable
          key={platform}
          style={[styles.button, selected === platform && styles.selected]}
          onPress={() => onSelect(platform)}
        >
          <Text style={styles.text}>Create for {capitalize(platform)}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 10, marginTop: 16 },
  button: {
    paddingVertical: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#374151',
    backgroundColor: '#1f2937'
  },
  selected: {
    borderColor: '#22c55e',
    backgroundColor: '#14532d'
  },
  text: { color: '#f9fafb', textAlign: 'center', fontWeight: '600' }
});

function capitalize(value: string): string {
  return value[0].toUpperCase() + value.slice(1);
}
