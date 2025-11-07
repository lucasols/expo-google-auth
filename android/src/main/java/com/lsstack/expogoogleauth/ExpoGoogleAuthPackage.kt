package com.lsstack.expogoogleauth

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import java.util.HashMap

class ExpoGoogleAuthPackage : BaseReactPackage() {
  override fun getModule(
    name: String,
    reactContext: ReactApplicationContext,
  ): NativeModule? =
    if (name == ExpoGoogleAuthModuleImpl.NAME) {
      ExpoGoogleAuthModule(reactContext)
    } else {
      null
    }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
    ReactModuleInfoProvider {
      val moduleInfos = HashMap<String, ReactModuleInfo>()
      val isTurboModule = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
      moduleInfos[ExpoGoogleAuthModuleImpl.NAME] =
        ReactModuleInfo(
          ExpoGoogleAuthModuleImpl.NAME,
          ExpoGoogleAuthModuleImpl.NAME,
          false,
          false,
          false,
          isTurboModule,
        )
      moduleInfos
    }
}
