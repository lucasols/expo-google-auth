# @ls-stack/expo-google-auth

Expo plugin and native module that provides Google authentication with platform-specific native integrations:

- **iOS** uses GoogleSignIn directly.
- **Android** uses the Android Credential Manager with the Google ID library.

The JavaScript API exposes a single `signIn()` method that resolves with Google tokens or `null` if the user cancels.

## Installation

```sh
pnpm add @ls-stack/expo-google-auth
```

## Configure Expo

Update your `app.json` or `app.config.ts` to include the config plugin and provide OAuth client IDs.

```json
{
  "expo": {
    "plugins": [
      [
        "@ls-stack/expo-google-auth/plugin",
        {
          "iosClientId": "YOUR_IOS_CLIENT_ID.apps.googleusercontent.com",
          "androidServerClientId": "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
        }
      ]
    ]
  }
}
```

### Optional Properties

- `iosUrlScheme`: Override the URL scheme used for callbacks. Defaults to the reversed `iosClientId`.

## iOS Setup

1. Ensure your app has a `GoogleService-Info.plist` or add `GIDClientID` to `Info.plist`.
2. Add the reversed client ID as a URL type (the plugin will add this if you provide `iosClientId`).
3. After installing the package, run `pod install` in your iOS directory.

The plugin patches `AppDelegate` to forward auth callbacks to `GIDSignIn`.

## Android Setup

1. Supply the `androidServerClientId` from your Google Cloud credentials (the Web client ID).
2. The config plugin writes the ID into `android/app/src/main/res/values/strings.xml`.

No additional code changes are required.

## Usage

```ts
import { signIn } from '@ls-stack/expo-google-auth';

const result = await signIn();
if (!result) {
  // user cancelled
  return;
}

console.log(result.idToken);
```

### Returned Shape

```ts
type GoogleLoginResult = {
  idToken: string;
  accessToken?: string;
  email?: string;
  name?: string;
  picture?: string;
};
```

## Error Handling

`signIn()` throws an `ExpoGoogleAuthError` when the native flow fails. The helper `isExpoGoogleAuthError` and the `code` property let you branch on known scenarios.

```ts
import {
  signIn,
  isExpoGoogleAuthError,
  ExpoGoogleAuthError,
} from '@ls-stack/expo-google-auth';

try {
  const result = await signIn();
  if (!result) return; // user cancelled
  // handle success
} catch (error) {
  if (isExpoGoogleAuthError(error)) {
    switch (error.code) {
      case 'PLAY_SERVICES_NOT_AVAILABLE':
        showToast('Google Play services are not available on this device.');
        return;
      case 'MISSING_CLIENT_ID':
        console.error('Check the plugin configuration in app.json/app.config.ts');
        return;
      case 'SIGN_IN_IN_PROGRESS':
        return; // already handling another sign-in flow
      default:
        showToast(error.message);
    }
  } else {
    console.error('Unexpected error during Google login', error);
  }
}
```

### Error Codes

| Code | Meaning |
| ---- | ------- |
| `SIGN_IN_IN_PROGRESS` | The native module already has an active sign-in flow. |
| `NO_ACTIVITY` | Android activity was unavailable (usually when the app is backgrounded). |
| `NO_VIEW_CONTROLLER` | iOS could not find a presented view controller. |
| `MISSING_CLIENT_ID` | OAuth client ID is missing; check plugin configuration. |
| `AUTHENTICATION_ERROR` | GoogleSignIn returned an authentication error. |
| `SIGNIN_ERROR` | General sign-in failure from GoogleSignIn. |
| `CREDENTIAL_ERROR` | Credential Manager could not produce a credential. |
| `CREDENTIAL_SECURITY_ERROR` | Credential Manager blocked the request due to security policy. |
| `CREDENTIAL_UNSUPPORTED` | Credential Manager cannot handle the requested option on this device. |
| `PLAY_SERVICES_NOT_AVAILABLE` | Google Play services (or the Google credential provider) is unavailable. |
| `UNEXPECTED_ERROR` | Platform threw an unexpected error. |
| `UNSUPPORTED_PLATFORM` | The library only runs on iOS and Android. |
| `UNKNOWN` | Fallback when no additional detail is available. |

## Development

- Build: `pnpm run build`
- Clean: `pnpm run clean`
- Lint: `pnpm run lint`
