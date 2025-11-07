import { Platform } from 'react-native';
import { requireNativeModule } from 'expo-modules-core';

const MODULE_NAME = 'ExpoGoogleAuth';

type NativeExpoGoogleAuthModule = {
  signIn(): Promise<GoogleLoginResult | null>;
};

const ExpoGoogleAuth: NativeExpoGoogleAuthModule =
  requireNativeModule<NativeExpoGoogleAuthModule>(MODULE_NAME);

export type GoogleLoginResult = {
  idToken: string;
  accessToken?: string;
  email?: string;
  name?: string;
  picture?: string;
};

export type GoogleAuthErrorCode =
  | 'SIGN_IN_IN_PROGRESS'
  | 'NO_ACTIVITY'
  | 'NO_VIEW_CONTROLLER'
  | 'MISSING_CLIENT_ID'
  | 'AUTHENTICATION_ERROR'
  | 'SIGNIN_ERROR'
  | 'CREDENTIAL_ERROR'
  | 'CREDENTIAL_SECURITY_ERROR'
  | 'CREDENTIAL_UNSUPPORTED'
  | 'PLAY_SERVICES_NOT_AVAILABLE'
  | 'UNEXPECTED_ERROR'
  | 'UNSUPPORTED_PLATFORM'
  | 'UNKNOWN';

export type ExpoGoogleAuthSupportedPlatform = 'ios' | 'android';

export type ExpoGoogleAuthErrorOptions = {
  code: GoogleAuthErrorCode;
  nativeCode?: string;
  nativeMessage?: string;
  platform?: ExpoGoogleAuthSupportedPlatform;
  cause?: unknown;
};

export class ExpoGoogleAuthError extends Error {
  readonly code: GoogleAuthErrorCode;
  readonly nativeCode?: string;
  readonly nativeMessage?: string;
  readonly platform?: ExpoGoogleAuthSupportedPlatform;
  readonly cause?: unknown;

  constructor(message: string, options: ExpoGoogleAuthErrorOptions) {
    super(message);
    this.name = 'ExpoGoogleAuthError';
    this.code = options.code;
    this.nativeCode = options.nativeCode;
    this.nativeMessage = options.nativeMessage;
    this.platform = options.platform;
    this.cause = options.cause;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

export function isExpoGoogleAuthError(error: unknown): error is ExpoGoogleAuthError {
  return error instanceof ExpoGoogleAuthError;
}

const DEFAULT_ERROR_MESSAGES: Record<GoogleAuthErrorCode, string> = {
  SIGN_IN_IN_PROGRESS: 'A Google sign-in operation is already in progress.',
  NO_ACTIVITY: 'Unable to find the current Android activity.',
  NO_VIEW_CONTROLLER: 'Unable to find the current iOS view controller.',
  MISSING_CLIENT_ID: 'Google client ID is missing. Check your configuration.',
  AUTHENTICATION_ERROR: 'Google authentication failed.',
  SIGNIN_ERROR: 'Google sign-in failed.',
  CREDENTIAL_ERROR: 'Credential Manager failed to provide a credential.',
  CREDENTIAL_SECURITY_ERROR: 'Credential Manager denied access due to security policies.',
  CREDENTIAL_UNSUPPORTED: 'Credential Manager does not support the requested option on this device.',
  PLAY_SERVICES_NOT_AVAILABLE: 'Google Play services are not available on this device.',
  UNEXPECTED_ERROR: 'An unexpected error occurred during Google sign-in.',
  UNSUPPORTED_PLATFORM: '@ls-stack/expo-google-auth only supports iOS and Android.',
  UNKNOWN: 'Google sign-in failed.',
};

const NATIVE_CODE_MAP: Record<string, GoogleAuthErrorCode> = {
  in_progress: 'SIGN_IN_IN_PROGRESS',
  no_activity: 'NO_ACTIVITY',
  no_view_controller: 'NO_VIEW_CONTROLLER',
  missing_client_id: 'MISSING_CLIENT_ID',
  authentication_error: 'AUTHENTICATION_ERROR',
  signin_error: 'SIGNIN_ERROR',
  credential_error: 'CREDENTIAL_ERROR',
  credential_security_error: 'CREDENTIAL_SECURITY_ERROR',
  credential_unsupported: 'CREDENTIAL_UNSUPPORTED',
  play_services_not_available: 'PLAY_SERVICES_NOT_AVAILABLE',
  unexpected_error: 'UNEXPECTED_ERROR',
};

type NativeErrorShape = {
  code?: string;
  message?: string;
};

function normalizePlatform(platform: string): ExpoGoogleAuthSupportedPlatform | undefined {
  return platform === 'ios' || platform === 'android' ? platform : undefined;
}

function mapNativeCode(nativeCode?: string): GoogleAuthErrorCode {
  if (!nativeCode) {
    return 'UNKNOWN';
  }

  const normalized = nativeCode.toLowerCase();
  return NATIVE_CODE_MAP[normalized] ?? 'UNKNOWN';
}

function pickMessage(code: GoogleAuthErrorCode, nativeMessage?: string): string {
  if (nativeMessage && nativeMessage.trim().length > 0) {
    return nativeMessage;
  }

  return DEFAULT_ERROR_MESSAGES[code] ?? DEFAULT_ERROR_MESSAGES.UNKNOWN;
}

function createErrorFromNative(error: unknown): ExpoGoogleAuthError {
  if (isExpoGoogleAuthError(error)) {
    return error;
  }

  const platform = normalizePlatform(Platform.OS);

  if (isRecord<NativeErrorShape>(error)) {
    const nativeCode = typeof error.code === 'string' ? error.code : undefined;
    const nativeMessage = typeof error.message === 'string' ? error.message : undefined;
    const code = mapNativeCode(nativeCode);
    const message = pickMessage(code, nativeMessage);

    return new ExpoGoogleAuthError(message, {
      code,
      nativeCode,
      nativeMessage,
      platform,
      cause: error instanceof Error ? error : undefined,
    });
  }

  const message =
    error instanceof Error && typeof error.message === 'string'
      ? error.message
      : DEFAULT_ERROR_MESSAGES.UNKNOWN;

  return new ExpoGoogleAuthError(message, {
    code: 'UNKNOWN',
    nativeMessage: message,
    platform,
    cause: error instanceof Error ? error : undefined,
  });
}

function isRecord<T extends Record<string, unknown>>(value: unknown): value is T {
  return typeof value === 'object' && value !== null;
}

/**
 * Launches the native Google sign-in flow. Returns the tokens/profile details
 * on success, or `null` if the user cancels the flow.
 */
export async function signIn(): Promise<GoogleLoginResult | null> {
  if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
    throw new ExpoGoogleAuthError(DEFAULT_ERROR_MESSAGES.UNSUPPORTED_PLATFORM, {
      code: 'UNSUPPORTED_PLATFORM',
      platform: normalizePlatform(Platform.OS),
    });
  }

  try {
    return await ExpoGoogleAuth.signIn();
  } catch (error) {
    throw createErrorFromNative(error);
  }
}
