package com.lsstack.expogoogleauth

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.turbomodule.core.interfaces.TurboModule

abstract class NativeExpoGoogleAuthSpec internal constructor(
  context: ReactApplicationContext,
) : ReactContextBaseJavaModule(context), TurboModule {
  @ReactMethod
  abstract fun signIn(promise: Promise)
}
