package com.lsstack.expogoogleauth

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = ExpoGoogleAuthModuleImpl.NAME)
class ExpoGoogleAuthModule(
  reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  private val moduleImpl = ExpoGoogleAuthModuleImpl(reactContext)

  override fun getName(): String = moduleImpl.getName()

  @ReactMethod
  fun signIn(promise: Promise) {
    moduleImpl.signIn(promise)
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    moduleImpl.invalidate()
  }
}
