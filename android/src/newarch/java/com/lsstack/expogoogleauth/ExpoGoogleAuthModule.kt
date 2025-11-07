package com.lsstack.expogoogleauth

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = ExpoGoogleAuthModuleImpl.NAME)
class ExpoGoogleAuthModule(
  reactContext: ReactApplicationContext,
) : NativeExpoGoogleAuthSpec(reactContext) {
  private val moduleImpl = ExpoGoogleAuthModuleImpl(reactContext)

  override fun getName(): String = moduleImpl.getName()

  override fun signIn(promise: Promise) {
    moduleImpl.signIn(promise)
  }

  override fun invalidate() {
    moduleImpl.invalidate()
    super.invalidate()
  }
}
