# Repository Guidelines

## Project Overview
`@ls-stack/expo-google-auth` delivers Google authentication for Expo apps by pairing runtime helpers in `src/` with a config plugin (`plugin/index.ts`) that patches native iOS/Android projects (e.g., adds GoogleSignIn handlers, writes Credential Manager IDs) so developers avoid manual native edits.

## Project Structure & Module Organization
- `src/` contains the TypeScript source for the JS runtime API; each module should export typed entry points mirrored in `build/src`.
- `plugin/` houses the Expo config plugin (e.g., `plugin/index.ts`) that patches native projects; keep helper utilities beside the plugin to share types.
- `android/` and `ios/` provide example native scaffolding for manual testing; prefer editing through config plugins instead of direct native changes.
- Generated artifacts land in `build/` after `pnpm build`; never edit files there manually—update the TypeScript sources instead.

## Build, Test, and Development Commands
- `pnpm build` transpiles TypeScript using `tsc` into the `build/` directory; run before publishing or linking locally.
- `pnpm clean` removes `build/`; run when switching branches to avoid stale artifacts.
- `pnpm lint` runs ESLint over `src/**` and `plugin/**`. The repo is mid-migration to the flat config format—create `eslint.config.js` if you add new rules so this command succeeds.

## Coding Style & Naming Conventions
- Use TypeScript with 2-space indentation and trailing commas where possible; rely on the default `tsconfig.json`.
- Favor descriptive, camelCase function names (`addSwiftGoogleSignIn`) and SCREAMING_SNAKE_CASE for constants mirrored in native resources.
- Keep native patch snippets minimal: extract reusable regex builders instead of duplicating literal strings.
- Lint with ESLint 9.x; prefer autofix (`pnpm lint --fix`) after adding the flat config.

## Testing Guidelines
- No automated test harness ships today; when adding features, include minimal reproduction projects under `ios/` or `android/` plus unit tests with Jest if logic can be isolated.
- Validate config-plugin changes by running `expo prebuild` in a sample app and confirming Xcode/Gradle compile cleanly.
- Document any new manual verification steps in PR descriptions until automated coverage exists.

## Commit & Pull Request Guidelines
- Write commits in imperative present tense (e.g., `fix AppDelegate handler matching`) and keep them scoped to a single concern.
- Every PR should describe the change, mention affected platforms (iOS/Android), note testing performed (`expo prebuild`, `pnpm build`, etc.), and link relevant issues.
- Include screenshots or log excerpts only when UI or runtime behavior changes; otherwise, provide reproduction steps for native patches.
