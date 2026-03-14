declare namespace JSX {
  interface Element {}
  interface IntrinsicElements {
    [elemName: string]: any;
  }
}

declare module 'react' {
  export type JSX = any;
  export function useState<T>(initial: T): [T, (value: T) => void];
  export function useMemo<T>(factory: () => T, deps: unknown[]): T;
  const React: any;
  export default React;
}

declare module 'react-native' {
  export const Alert: any;
  export const Linking: any;
  export const Pressable: any;
  export const SafeAreaView: any;
  export const ScrollView: any;
  export const StatusBar: any;
  export const StyleSheet: any;
  export const Switch: any;
  export const Text: any;
  export const TextInput: any;
  export const View: any;
}

declare module 'axios' {
  const axios: any;
  export default axios;
}

declare const process: {
  env: Record<string, string | undefined>;
};


declare module 'react/jsx-runtime' {
  export const jsx: any;
  export const jsxs: any;
  export const Fragment: any;
}
