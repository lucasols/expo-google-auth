import {
  AndroidConfig,
  type ConfigPlugin,
  IOSConfig,
  WarningAggregator,
  createRunOncePlugin,
  withAppDelegate,
  withInfoPlist,
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
  config = withGoogleInfoPlist(config, props)
  config = withGoogleAppDelegate(config)
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

const withGoogleAppDelegate: ConfigPlugin = (config) => {
  return withAppDelegate(config, (innerConfig) => {
    const { modResults } = innerConfig
    if (modResults.language === 'swift') {
      modResults.contents = addSwiftGoogleSignIn(modResults.contents)
    } else if (modResults.language === 'objc') {
      modResults.contents = addObjcGoogleSignIn(modResults.contents)
    } else {
      WarningAggregator.addWarningIOS(
        '@ls-stack/expo-google-auth',
        `Unsupported AppDelegate language "${modResults.language}". Please add GoogleSignIn URL handling manually.`
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

function addSwiftGoogleSignIn(contents: string): string {
  let output = contents

  if (!output.includes('import GoogleSignIn')) {
    const importPattern = /import\s+ExpoModulesCore.*\n/
    if (importPattern.test(output)) {
      output = output.replace(
        importPattern,
        (match) => `${match}import GoogleSignIn\n`
      )
    } else {
      output = `import GoogleSignIn\n${output}`
    }
  }

  const handlerSnippet =
    '    if GIDSignIn.sharedInstance.handle(url) {\n      return true\n    }\n'

  if (/GIDSignIn\.sharedInstance(?:\s*\.\s*)?handle\(url\)/.test(output)) {
    return output
  }

  const swiftOpenUrlHandlerRegex =
    /func\s+application\s*\([\s\S]*?open\s+\w+\s*:\s*URL[\s\S]*?options\s*:\s*\[\s*UIApplication\.OpenURLOptionsKey\s*:\s*Any\??\s*\]\??\s*(?:=\s*\[\s*:\s*\])?\s*\)\s*->\s*Bool\s*\{/

  const applicationSignature =
    'func application(_ application: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool'

  if (swiftOpenUrlHandlerRegex.test(output)) {
    output = output.replace(
      swiftOpenUrlHandlerRegex,
      (match) => `${match}\n${handlerSnippet}`
    )
    return output
  }

  const method = `
  ${applicationSignature} {
${handlerSnippet}    return false
  }
`

  const insertionIndex = output.lastIndexOf('}')
  if (insertionIndex !== -1) {
    output = `${output.slice(0, insertionIndex)}${method}${output.slice(
      insertionIndex
    )}`
  } else {
    output += method
  }

  return output
}

function addObjcGoogleSignIn(contents: string): string {
  let output = contents

  if (!output.includes('#import <GoogleSignIn/GoogleSignIn.h>')) {
    const importPattern = /#import "AppDelegate\.h"\n/
    if (importPattern.test(output)) {
      output = output.replace(
        importPattern,
        (match) => `${match}#import <GoogleSignIn/GoogleSignIn.h>\n`
      )
    } else {
      output = `#import <GoogleSignIn/GoogleSignIn.h>\n${output}`
    }
  }

  const handlerSnippet =
    '  if ([GIDSignIn.sharedInstance handleURL:url]) {\n    return YES;\n  }\n'

  if (/GIDSignIn(?:\.| )sharedInstance[\s\S]*?handleURL:url/.test(output)) {
    return output
  }

  const objcOpenUrlHandlerRegex =
    /-\s*\(\s*BOOL\s*\)\s*application:\s*\(\s*UIApplication\s*\*\s*\)\s*\w+[\s\S]*?openURL:\s*\(\s*NSURL\s*\*\s*\)\s*\w+[\s\S]*?options:\s*\(\s*NSDictionary\s*<\s*UIApplicationOpenURLOptionsKey\s*,\s*id\s*>\s*\*\s*\)\s*\w+\s*\{/

  const applicationSignature =
    '- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options'

  if (objcOpenUrlHandlerRegex.test(output)) {
    output = output.replace(
      objcOpenUrlHandlerRegex,
      (match) => `${match}\n${handlerSnippet}`
    )
    return output
  }
  const method = `
${applicationSignature} {
${handlerSnippet}  return NO;
}
`

  const anchor = output.lastIndexOf('@end')
  if (anchor !== -1) {
    output = `${output.slice(0, anchor)}${method}\n@end`
  } else {
    output += method
  }

  return output
}

const plugin = createRunOncePlugin(withHybridGoogle, pkg.name, pkg.version)

export default plugin
export { withHybridGoogle }
