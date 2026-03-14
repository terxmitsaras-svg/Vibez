import React from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

interface Props {
  value: string;
  onChange: (value: string) => void;
}

export default function VibeInput({ value, onChange }: Props): JSX.Element {
  return (
    <View style={styles.wrapper}>
      <TextInput
        value={value}
        onChangeText={onChange}
        placeholder="Describe your vibe..."
        placeholderTextColor="#9ca3af"
        style={styles.input}
        multiline
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    width: '100%'
  },
  input: {
    minHeight: 90,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#374151',
    color: '#f9fafb',
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#1f2937'
  }
});
