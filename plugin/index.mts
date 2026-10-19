import {
  AndroidConfig,
  type ConfigPlugin,
  IOSConfig,
  WarningAggregator,
  createRunOncePlugin,
  withInfoPlist,
  withPodfile,
  withStringsXml,
} from 'expo/config-plugins'

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
   * Web client ID used by iOS when your backend needs `serverClientID` behaviour.
   * Written to Info.plist as GIDServerClientID.
   */
  iosServerClientId?: string
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
  config = withLegacyGoogleSignInPodRemoved(config)
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

    if (props.iosServerClientId) {
      infoPlist.GIDServerClientID = props.iosServerClientId
    }

    if (urlScheme) {
      innerConfig.modResults = IOSConfig.Scheme.appendScheme(
        urlScheme,
        infoPlist
      )
    } else if (!innerConfig.ios?.googleServicesFile) {
      // With `ios.googleServicesFile`, Expo adds the REVERSED_CLIENT_ID scheme itself
      WarningAggregator.addWarningIOS(
        '@ls-stack/expo-google-auth',
        'You must provide "iosClientId" (Google OAuth client ID) or "ios.googleServicesFile" for iOS to complete configuration.'
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

// Versions <= 0.4.0 pinned GoogleSignIn 7.0.0 in the Podfile, which conflicts with the podspec range
const LEGACY_GOOGLE_SIGN_IN_POD_LINE_PATTERN =
  /^[ \t]*pod 'GoogleSignIn', '7\.0\.0'[ \t]*\r?\n/m

const withLegacyGoogleSignInPodRemoved: ConfigPlugin = (config) => {
  return withPodfile(config, (innerConfig) => {
    innerConfig.modResults.contents = innerConfig.modResults.contents.replace(
      LEGACY_GOOGLE_SIGN_IN_POD_LINE_PATTERN,
      ''
    )
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

const plugin = createRunOncePlugin(withHybridGoogle, pkg.name, pkg.version)

export default plugin
export { withHybridGoogle }
