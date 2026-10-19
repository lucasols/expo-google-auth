package com.lsstack.expogoogleauth

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

private const val SERVER_CLIENT_ID_RESOURCE = "ls_stack_google_server_client_id"

// Codes must match NATIVE_CODE_MAP in src/index.mts
private object NativeErrorCode {
  const val IN_PROGRESS = "in_progress"
  const val NO_ACTIVITY = "no_activity"
  const val MISSING_CLIENT_ID = "missing_client_id"
  const val NO_CREDENTIAL = "no_credential"
  const val CREDENTIAL_ERROR = "credential_error"
  const val CREDENTIAL_SECURITY_ERROR = "credential_security_error"
  const val CREDENTIAL_UNSUPPORTED = "credential_unsupported"
  const val PLAY_SERVICES_NOT_AVAILABLE = "play_services_not_available"
}

class ExpoGoogleAuthModule : Module() {
  private val isSigningIn = AtomicBoolean(false)

  override fun definition() = ModuleDefinition {
    Name("ExpoGoogleAuth")

    AsyncFunction("signIn") Coroutine { ->
      if (!isSigningIn.compareAndSet(false, true)) {
        throw CodedException(NativeErrorCode.IN_PROGRESS, "A Google sign-in request is already running.", null)
      }

      try {
        val activity = appContext.currentActivity
          ?: throw CodedException(NativeErrorCode.NO_ACTIVITY, "Unable to find the current Activity.", null)

        val serverClientId = resolveServerClientId()
          ?: throw CodedException(
            NativeErrorCode.MISSING_CLIENT_ID,
            "Set androidServerClientId via the Expo config plugin (writes to strings.xml).",
            null,
          )

        return@Coroutine requestGoogleIdToken(activity, serverClientId)
      } finally {
        isSigningIn.set(false)
      }
    }
  }

  private suspend fun requestGoogleIdToken(activity: Activity, serverClientId: String): Map<String, String?>? {
    val googleIdOption = GetGoogleIdOption.Builder()
      .setServerClientId(serverClientId)
      .setFilterByAuthorizedAccounts(false)
      .setAutoSelectEnabled(true)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    val response = try {
      // An Activity context is required so the account picker launches in the app's task
      CredentialManager.create(activity).getCredential(context = activity, request = request)
    } catch (_: GetCredentialCancellationException) {
      return null
    } catch (error: GetCredentialException) {
      throw mapCredentialException(error)
    }

    return handleCredentialResponse(response)
  }

  private fun mapCredentialException(error: GetCredentialException): CodedException {
    val message = error.message ?: "Credential request failed."

    return when {
      error is NoCredentialException -> CodedException(
        NativeErrorCode.NO_CREDENTIAL,
        "No Google account is available. Check that a Google account is on the device and that the server client ID and app signing SHA-1 are configured in Google Cloud. ($message)",
        error,
      )
      error is GetCredentialProviderConfigurationException ->
        CodedException(NativeErrorCode.PLAY_SERVICES_NOT_AVAILABLE, "Credential provider is not available: $message", error)
      error is GetCredentialUnsupportedException ->
        CodedException(NativeErrorCode.CREDENTIAL_UNSUPPORTED, "Credential provider does not support this request: $message", error)
      error.type.contains("SECURITY", ignoreCase = true) ->
        CodedException(NativeErrorCode.CREDENTIAL_SECURITY_ERROR, "Credential provider blocked the request for security reasons: $message", error)
      else -> CodedException(NativeErrorCode.CREDENTIAL_ERROR, "Credential error: $message", error)
    }
  }

  private fun handleCredentialResponse(response: GetCredentialResponse): Map<String, String?> {
    val credential = response.credential

    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      throw CodedException(NativeErrorCode.CREDENTIAL_ERROR, "Unexpected credential type: ${credential.type}", null)
    }

    val googleCredential = try {
      GoogleIdTokenCredential.createFrom(credential.data)
    } catch (error: GoogleIdTokenParsingException) {
      throw CodedException(NativeErrorCode.CREDENTIAL_ERROR, "Unable to parse the Google ID token credential.", error)
    }

    return mapOf(
      "idToken" to googleCredential.idToken,
      "email" to extractEmail(googleCredential.idToken),
      "name" to googleCredential.displayName,
      "picture" to googleCredential.profilePictureUri?.toString()
    )
  }

  private fun resolveServerClientId(): String? {
    val reactContext = appContext.reactContext ?: return null
    val resId = reactContext.resources.getIdentifier(
      SERVER_CLIENT_ID_RESOURCE,
      "string",
      reactContext.packageName,
    )

    return if (resId != 0) reactContext.getString(resId).takeIf { it.isNotEmpty() } else null
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
      json.optString("email").takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
      null
    }
  }
}
