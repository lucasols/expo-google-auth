import {
  AndroidConfig,
  type ConfigPlugin,
  IOSConfig,
  WarningAggregator,
  createRunOncePlugin,
  withInfoPlist,
  withPodfile,
  withStringsXml,
} from '@expo/config-plugins'

import pkg from '../package.json' with { type: 'json' }

export type HybridGooglePluginProps = {
  /**
   * iOS client ID from Google Cloud console (typically ends in .apps.googleusercontent.com).
   * Written to Info.plist as GIDClientID.
   */
  iosClientId?: string
  /**
   * Custom URL scheme used by Google sign-in redirect. Defaults to reversed client ID.
   */
  iosUrlScheme?: string
  /**
   * Android web client ID used when requesting an ID token with Credential Manager.
   */
  androidServerClientId?: string
}

const ANDROID_STRING_NAME = 'ls_stack_google_server_client_id'

const withHybridGoogle: ConfigPlugin<HybridGooglePluginProps> = (
  config,
  props = {}
) => {
  config = withHybridGoogleIos(config, props)
  config = withHybridGoogleAndroid(config, props)
  return config
}

const withHybridGoogleIos: ConfigPlugin<HybridGooglePluginProps> = (
  config,
  props
) => {
  config = withGoogleSignInCocoaPods(config)
  config = withGoogleInfoPlist(config, props)
  return config
}

const withGoogleInfoPlist: ConfigPlugin<HybridGooglePluginProps> = (
  config,
  props
) => {
  return withInfoPlist(config, (innerConfig) => {
    const infoPlist = innerConfig.modResults
    const urlScheme = resolveIosUrlScheme(props)

    if (props.iosClientId) {
      infoPlist.GIDClientID = props.iosClientId
    }

    if (urlScheme) {
      innerConfig.modResults = IOSConfig.Scheme.appendScheme(
        urlScheme,
        infoPlist
      )
    } else if (!props.iosClientId) {
      WarningAggregator.addWarningIOS(
        '@ls-stack/expo-google-auth',
        'You must provide "iosClientId" (Google OAuth client ID) for iOS to complete configuration.'
      )
    }

    return innerConfig
  })
}

const withHybridGoogleAndroid: ConfigPlugin<HybridGooglePluginProps> = (
  config,
  props
) => {
  return withStringsXml(config, (innerConfig) => {
    const serverClientId = props.androidServerClientId
    if (!serverClientId) {
      WarningAggregator.addWarningAndroid(
        '@ls-stack/expo-google-auth',
        'Consider setting "androidServerClientId" so Credential Manager can return a Google ID token.'
      )
      return innerConfig
    }

    const item = AndroidConfig.Resources.buildResourceItem({
      name: ANDROID_STRING_NAME,
      value: serverClientId,
      translatable: false,
    })

    innerConfig.modResults = AndroidConfig.Strings.setStringItem(
      [item],
      innerConfig.modResults
    )
    return innerConfig
  })
}

const withGoogleSignInCocoaPods: ConfigPlugin = (config) => {
  return withPodfile(config, (innerConfig) => {
    const contents = innerConfig.modResults.contents
    innerConfig.modResults.contents = ensureGoogleSignInPod(contents)
    return innerConfig
  })
}

function resolveIosUrlScheme(
  props: HybridGooglePluginProps
): string | undefined {
  if (props.iosUrlScheme) {
    return props.iosUrlScheme
  }

  if (!props.iosClientId) {
    return undefined
  }

  return props.iosClientId.split('.').reverse().join('.')
}

const GOOGLE_SIGN_IN_POD_PATTERN = /\bpod\s+['"]GoogleSignIn['"]/
const PODFILE_USE_NATIVE_MODULES_PATTERN = /(use_native_modules!\s*\n)/
const PODFILE_TARGET_PATTERN = /(target\s+['"][^'"]+['"]\s+do\s*\n)/
const GOOGLE_SIGN_IN_POD_DECLARATION = "  pod 'GoogleSignIn', '7.0.0'"

function ensureGoogleSignInPod(contents: string): string {
  if (GOOGLE_SIGN_IN_POD_PATTERN.test(contents)) {
    return contents
  }

  const insertion = `${GOOGLE_SIGN_IN_POD_DECLARATION}\n`

  if (PODFILE_USE_NATIVE_MODULES_PATTERN.test(contents)) {
    return contents.replace(PODFILE_USE_NATIVE_MODULES_PATTERN, `$1${insertion}`)
  }

  if (PODFILE_TARGET_PATTERN.test(contents)) {
    return contents.replace(PODFILE_TARGET_PATTERN, `$1${insertion}`)
  }

  throw new Error(
    'Unable to automatically add GoogleSignIn pod. Please ensure your ios/Podfile contains a target block and add `pod \'GoogleSignIn\', \'7.0.0\'` manually.'
  )
}

const plugin = createRunOncePlugin(withHybridGoogle, pkg.name, pkg.version)

export default plugin
export { withHybridGoogle }
