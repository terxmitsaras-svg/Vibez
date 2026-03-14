import React, { useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { generatePlaylistVariants } from '../api/client';
import { PlatformType, PlaylistVariant } from '../types';

const PLATFORMS: PlatformType[] = ['spotify', 'youtube', 'soundcloud', 'deezer'];

interface Props {
  onVariantsGenerated: (variants: PlaylistVariant[]) => void;
}

export default function HomeScreen({ onVariantsGenerated }: Props): JSX.Element {
  const [platform, setPlatform] = useState<PlatformType>('spotify');
  const [isConnected, setIsConnected] = useState(false);
  const [isDark, setIsDark] = useState(true);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);

  const colors = useMemo(
    () =>
      isDark
        ? {
            bg: '#050505',
            panel: '#0e0e0e',
            text: '#ffffff',
            muted: '#d7d7d7',
            mustard: '#f8c623',
            red: '#f83322',
            chipBorder: '#f8c623'
          }
        : {
            bg: '#fffdf8',
            panel: '#fff4d4',
            text: '#111111',
            muted: '#3f3f3f',
            mustard: '#d39f00',
            red: '#ea2b1f',
            chipBorder: '#ea2b1f'
          },
    [isDark]
  );

  const connectButtonLabel = isConnected ? `Connected to ${capitalize(platform)}` : `Connect ${capitalize(platform)}`;

  const handleGenerate = async (): Promise<void> => {
    if (!query.trim()) return;
    if (!isConnected) {
      Alert.alert('Connect platform', 'Please connect a music platform first.');
      return;
    }

    try {
      setLoading(true);
      const variants = await generatePlaylistVariants({
        userId: '00000000-0000-0000-0000-000000000001',
        platform,
        vibeText: query
      });
      onVariantsGenerated(variants);
    } catch (error) {
      Alert.alert('Generation failed', error instanceof Error ? error.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={styles.topRow}>
        <View style={[styles.wordmark, { backgroundColor: colors.panel, borderColor: colors.mustard }]}> 
          <Text style={[styles.wordmarkV, { color: colors.red }]}>V</Text>
          <Text style={[styles.wordmarkRest, { color: colors.text }]}>ibez</Text>
        </View>

        <View style={styles.topActions}>
          <Pressable
            style={[styles.menuButton, { borderColor: colors.chipBorder }]}
            onPress={() => Alert.alert('Menu', 'Navigation menu coming soon.')}
          >
            <Text style={[styles.menuIcon, { color: colors.text }]}>☰</Text>
          </Pressable>
          <Switch
            value={isDark}
            onValueChange={setIsDark}
            trackColor={{ false: '#f3d47a', true: '#f83322' }}
            thumbColor="#ffffff"
          />
        </View>
      </View>

      <View style={[styles.banner, { backgroundColor: colors.panel, borderColor: colors.red }]}>
        <Text style={[styles.bannerTitle, { color: colors.text }]}>Vibez</Text>
        <Text style={[styles.bannerSubtitle, { color: colors.text }]}>Describe <Text style={[styles.accent, { color: colors.mustard }]}>the vibe</Text>.</Text>
        <Text style={[styles.bannerSubtitle, { color: colors.text }]}>Get the <Text style={[styles.accent, { color: colors.mustard }]}>perfect playlist</Text>.</Text>
      </View>

      <View style={styles.platformRow}>
        {PLATFORMS.map((item) => (
          <Pressable
            key={item}
            onPress={() => {
              setPlatform(item);
              setIsConnected(false);
            }}
            style={[
              styles.platformChip,
              { borderColor: colors.chipBorder, backgroundColor: isDark ? '#111111' : '#fff9e8' },
              platform === item && { backgroundColor: colors.red }
            ]}
          >
            <Text style={[styles.platformText, { color: platform === item ? '#ffffff' : colors.text }]}>{capitalize(item)}</Text>
          </Pressable>
        ))}
      </View>

      <Pressable
        onPress={() => setIsConnected(true)}
        style={[styles.connectBtn, { borderColor: colors.mustard, backgroundColor: isConnected ? colors.mustard : 'transparent' }]}
      >
        <Text style={[styles.connectText, { color: isConnected ? '#111111' : colors.text }]}>{connectButtonLabel}</Text>
      </Pressable>

      <View style={[styles.inputWrap, { borderColor: colors.chipBorder, backgroundColor: colors.panel }]}>
        <TextInput
          style={[styles.input, { color: colors.text }]}
          placeholder="Mood, genre, artist, moment..."
          placeholderTextColor={isDark ? '#a7a7a7' : '#5e5e5e'}
          value={query}
          onChangeText={setQuery}
        />
      </View>

      <Pressable
        disabled={loading}
        onPress={handleGenerate}
        style={[styles.generateBtn, { backgroundColor: colors.red }, loading && styles.disabled]}
      >
        <Text style={styles.generateText}>{loading ? 'Generating 3 variants...' : 'Generate'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    gap: 14
  },
  topRow: {
    marginTop: 8,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  wordmark: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 6
  },
  wordmarkV: { fontSize: 28, fontWeight: '900' },
  wordmarkRest: { fontSize: 28, fontWeight: '700' },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  menuButton: {
    borderWidth: 1,
    borderRadius: 10,
    height: 36,
    width: 36,
    alignItems: 'center',
    justifyContent: 'center'
  },
  menuIcon: { fontSize: 17, fontWeight: '700' },
  banner: {
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 16
  },
  bannerTitle: {
    fontSize: 34,
    fontWeight: '900',
    marginBottom: 4
  },
  bannerSubtitle: {
    fontSize: 26,
    fontWeight: '800',
    lineHeight: 32
  },
  accent: {
    fontWeight: '900'
  },
  platformRow: {
    marginTop: 2,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8
  },
  platformChip: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  platformText: { fontWeight: '700' },
  connectBtn: {
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 11,
    alignItems: 'center'
  },
  connectText: { fontWeight: '700' },
  inputWrap: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12
  },
  input: {
    minHeight: 52,
    fontSize: 16
  },
  generateBtn: {
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center'
  },
  disabled: { opacity: 0.6 },
  generateText: {
    color: '#fff',
    fontWeight: '800',
    letterSpacing: 0.4
  }
});

function capitalize(value: string): string {
  return value[0].toUpperCase() + value.slice(1);
}
