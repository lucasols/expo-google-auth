import { defineConfig } from 'tsdown'

export default defineConfig({
  entry: ['src/index.mts', 'plugin/index.mts'],
  format: ['esm', 'cjs'],
  dts: true,
  clean: true,
  external: [
    'expo',
    'expo/config-plugins',
    'react-native',
  ],
})
