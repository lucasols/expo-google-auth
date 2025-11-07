import { defineConfig } from 'tsdown'

export default defineConfig({
  entry: ['src/index.mts', 'plugin/index.mts'],
  format: ['esm', 'cjs'],
  dts: true,
  clean: true,
  external: [
    'expo-modules-core',
    'react-native',
    '@expo/config-plugins',
  ],
})
