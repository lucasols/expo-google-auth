import { defineConfig } from 'tsdown'

export default defineConfig({
  entry: ['src/index.ts', 'plugin/index.ts'],
  format: ['esm'],
  dts: true,
  clean: true,
})
