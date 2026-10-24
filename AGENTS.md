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

## Git and Publishing Workflow

These are pre-approved, do them without asking the user for confirmation:

- Always deliver changes through a pull request: create a branch from `main`, commit, push and open the PR with `gh pr create`. Never commit directly to `main`
- Publish packages only from `main`, after the PR with the changes is merged: switch to an up-to-date `main` and run `pkg-manager publish --type <patch|minor|major>` from the repo root

## Coding Style & Naming Conventions

- Use TypeScript with 2-space indentation and trailing commas where possible; rely on the default `tsconfig.json`.
- Favor descriptive, camelCase function names (`addSwiftGoogleSignIn`) and SCREAMING_SNAKE_CASE for constants mirrored in native resources.
- Keep native patch snippets minimal: extract reusable regex builders instead of duplicating literal strings.
- Lint with ESLint 9.x; prefer autofix (`pnpm lint --fix`) after adding the flat config.
