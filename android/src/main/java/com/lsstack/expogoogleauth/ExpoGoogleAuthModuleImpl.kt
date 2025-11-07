package com.lsstack.expogoogleauth

import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
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

class ExpoGoogleAuthModuleImpl(
  private val reactContext: ReactApplicationContext,
) {
  companion object {
    const val NAME = "ExpoGoogleAuth"
  }

  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  fun getName(): String = NAME

  fun invalidate() {
    coroutineScope.cancel()
  }

  fun signIn(promise: Promise) {
    if (reactContext.currentActivity == null) {
      promise.reject("no_activity", "Unable to find the current Activity.")
      return
    }

    val serverClientId = resolveServerClientId()
    if (serverClientId.isNullOrEmpty()) {
      promise.reject(
        "missing_client_id",
        "Set androidServerClientId via the Expo config plugin (writes to strings.xml).",
      )
      return
    }

    coroutineScope.launch {
      try {
        val credentialManager = CredentialManager.create(reactContext)
        val googleIdOption = GetGoogleIdOption.Builder()
          .setServerClientId(serverClientId)
          .setFilterByAuthorizedAccounts(false)
          .setAutoSelectEnabled(true)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

        val response = credentialManager.getCredential(
          context = reactContext,
          request = request,
        )
        handleCredentialResponse(response, promise)
      } catch (cancellation: GetCredentialCancellationException) {
        promise.resolve(null)
      } catch (error: GetCredentialException) {
        when {
          error is NoCredentialException -> promise.resolve(null)
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialProviderConfigurationException",
          ) ->
            promise.reject(
              "play_services_not_available",
              error.message ?: "Credential provider is not available",
              error,
            )
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialUnsupportedException",
          ) ->
            promise.reject(
              "credential_unsupported",
              error.message ?: "Credential provider does not support this request",
              error,
            )
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialSecurityException",
          ) ->
            promise.reject(
              "credential_security_error",
              error.message
                ?: "Credential provider blocked the request for security reasons",
              error,
            )
          else -> {
            val errorType = error.type ?: "credential_error"
            val errorMessage = error.message ?: "Credential request failed"
            if (errorType.contains("NoCredentialAvailable", ignoreCase = true)) {
              promise.resolve(null)
            } else if (errorType.contains("Configuration", ignoreCase = true)) {
              promise.reject("play_services_not_available", errorMessage, error)
            } else if (errorType.contains("Unsupported", ignoreCase = true)) {
              promise.reject("credential_unsupported", errorMessage, error)
            } else if (errorType.contains("Security", ignoreCase = true)) {
              promise.reject("credential_security_error", errorMessage, error)
            } else {
              promise.reject("credential_error", errorMessage, error)
            }
          }
        }
      } catch (error: Exception) {
        promise.reject("unexpected_error", error.message, error)
      }
    }
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
      reactContext.packageName,
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

  private fun hasExceptionType(
    error: GetCredentialException,
    className: String,
  ): Boolean {
    return try {
      val clazz = Class.forName(className)
      clazz.isInstance(error)
    } catch (_: ClassNotFoundException) {
      false
    }
  }
}
