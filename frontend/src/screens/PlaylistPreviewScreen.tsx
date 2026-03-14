import React from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { PlaylistVariant } from '../types';

interface Props {
  variants: PlaylistVariant[];
  onBack: () => void;
}

export default function PlaylistPreviewScreen({ variants, onBack }: Props): JSX.Element {
  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Text style={styles.title}>3 Vibez Variants</Text>
        <Pressable onPress={onBack} style={styles.backBtn}>
          <Text style={styles.backText}>Back</Text>
        </Pressable>
      </View>
      <ScrollView>
        {variants.map((variant) => (
          <View key={variant.id} style={styles.card}>
            <Text style={styles.variantTitle}>{variant.title}</Text>
            <Text style={styles.playlistName}>{variant.playlist.playlistName}</Text>
            {variant.playlist.tracks.slice(0, 8).map((track) => (
              <Text style={styles.trackLine} key={`${variant.id}-${track.trackId}`}>
                • {track.title} — {track.artist}
              </Text>
            ))}
            <Pressable style={styles.openBtn} onPress={() => Linking.openURL(variant.playlist.playlistUrl)}>
              <Text style={styles.openText}>Open Playlist</Text>
            </Pressable>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a0a',
    padding: 16
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12
  },
  title: {
    color: '#ffffff',
    fontSize: 22,
    fontWeight: '800'
  },
  backBtn: {
    borderWidth: 1,
    borderColor: '#ffcc33',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 6
  },
  backText: { color: '#ffcc33', fontWeight: '700' },
  card: {
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#f52a21',
    borderRadius: 14,
    backgroundColor: '#161616',
    padding: 12
  },
  variantTitle: {
    color: '#ffcc33',
    fontWeight: '700'
  },
  playlistName: {
    color: '#ffffff',
    fontWeight: '700',
    marginTop: 4,
    marginBottom: 8
  },
  trackLine: {
    color: '#ffffff',
    opacity: 0.9,
    marginBottom: 4
  },
  openBtn: {
    marginTop: 8,
    backgroundColor: '#f52a21',
    borderRadius: 10,
    paddingVertical: 10
  },
  openText: {
    color: '#ffffff',
    fontWeight: '700',
    textAlign: 'center'
  }
});
