package com.lsstack.expogoogleauth

import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialSecurityException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialAvailableException
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.module.annotations.ReactModule
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.text.Charsets

private const val SERVER_CLIENT_ID_RESOURCE = "ls_stack_google_server_client_id"

@ReactModule(name = ExpoGoogleAuthModule.NAME)
class ExpoGoogleAuthModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "ExpoGoogleAuth"
  }

  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override fun getName(): String = NAME

  @ReactMethod
  fun signIn(promise: Promise) {
    val activity = currentActivity
    if (activity == null) {
      promise.reject("no_activity", "Unable to find the current Activity.")
      return
    }

    val serverClientId = resolveServerClientId()
    if (serverClientId.isNullOrEmpty()) {
      promise.reject(
        "missing_client_id",
        "Set androidServerClientId via the Expo config plugin (writes to strings.xml)."
      )
      return
    }

    coroutineScope.launch {
      try {
        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
          .setServerClientId(serverClientId)
          .setFilterByAuthorizedAccounts(false)
          .setAutoSelectEnabled(true)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

        val response = credentialManager.getCredential(
          context = activity,
          request = request
        )
        handleCredentialResponse(response, promise)
      } catch (cancellation: GetCredentialCancellationException) {
        promise.resolve(null)
      } catch (noCreds: NoCredentialAvailableException) {
        promise.resolve(null)
      } catch (providerConfig: GetCredentialProviderConfigurationException) {
        promise.reject("play_services_not_available", providerConfig.message, providerConfig)
      } catch (unsupported: GetCredentialUnsupportedException) {
        promise.reject("credential_unsupported", unsupported.message, unsupported)
      } catch (security: GetCredentialSecurityException) {
        promise.reject("credential_security_error", security.message, security)
      } catch (unknown: GetCredentialUnknownException) {
        promise.reject("credential_error", unknown.message, unknown)
      } catch (error: GetCredentialException) {
        promise.reject("credential_error", error.message, error)
      } catch (error: Exception) {
        promise.reject("unexpected_error", error.message, error)
      }
    }
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    coroutineScope.cancel()
  }

  private fun handleCredentialResponse(response: GetCredentialResponse, promise: Promise) {
    val credential = response.credential

    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
      val result = Arguments.createMap().apply {
        putString("idToken", googleCredential.idToken)
        putString("email", extractEmail(googleCredential.idToken))
        putString("name", googleCredential.displayName)
        putString("picture", googleCredential.profilePictureUri?.toString())
      }
      promise.resolve(result)
    } else {
      promise.resolve(null)
    }
  }

  private fun resolveServerClientId(): String? {
    val resId = reactContext.resources.getIdentifier(
      SERVER_CLIENT_ID_RESOURCE,
      "string",
      reactContext.packageName
    )

    return if (resId != 0) reactContext.getString(resId) else null
  }

  private fun extractEmail(idToken: String): String? {
    return try {
      val parts = idToken.split(".")
      if (parts.size < 2) {
        return null
      }
      val payload = parts[1]
      val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
      val json = JSONObject(String(decoded, Charsets.UTF_8))
      json.optString("email", null).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
      null
    }
  }
}
