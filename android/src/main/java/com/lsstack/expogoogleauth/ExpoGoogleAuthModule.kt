package com.lsstack.expogoogleauth

import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject

private const val SERVER_CLIENT_ID_RESOURCE = "ls_stack_google_server_client_id"

class ExpoGoogleAuthModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoGoogleAuth")

    AsyncFunction("signIn") Coroutine { ->
      val currentActivity = appContext.currentActivity
        ?: throw Exception("Unable to find the current Activity.")

      val serverClientId = resolveServerClientId()
        ?: throw Exception("Set androidServerClientId via the Expo config plugin (writes to strings.xml).")

      try {
        val credentialManager = CredentialManager.create(appContext.reactContext!!)
        val googleIdOption = GetGoogleIdOption.Builder()
          .setServerClientId(serverClientId)
          .setFilterByAuthorizedAccounts(false)
          .setAutoSelectEnabled(true)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

        val response = credentialManager.getCredential(
          context = appContext.reactContext!!,
          request = request,
        )
        return@Coroutine handleCredentialResponse(response)
      } catch (cancellation: GetCredentialCancellationException) {
        return@Coroutine null
      } catch (error: GetCredentialException) {
        when {
          error is NoCredentialException -> return@Coroutine null
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialProviderConfigurationException",
          ) ->
            throw Exception("Credential provider is not available: ${error.message}")
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialUnsupportedException",
          ) ->
            throw Exception("Credential provider does not support this request: ${error.message}")
          hasExceptionType(
            error,
            "androidx.credentials.exceptions.GetCredentialSecurityException",
          ) ->
            throw Exception("Credential provider blocked the request for security reasons: ${error.message}")
          else -> {
            val errorType = error.type ?: "credential_error"
            val errorMessage = error.message ?: "Credential request failed"
            if (errorType.contains("NoCredentialAvailable", ignoreCase = true)) {
              return@Coroutine null
            } else if (errorType.contains("Configuration", ignoreCase = true)) {
              throw Exception("Play services not available: $errorMessage")
            } else if (errorType.contains("Unsupported", ignoreCase = true)) {
              throw Exception("Credential unsupported: $errorMessage")
            } else if (errorType.contains("Security", ignoreCase = true)) {
              throw Exception("Credential security error: $errorMessage")
            } else {
              throw Exception("Credential error: $errorMessage")
            }
          }
        }
      } catch (error: Exception) {
        throw error
      }
    }
  }

  private fun handleCredentialResponse(response: GetCredentialResponse): Map<String, String?>? {
    val credential = response.credential

    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
      return mapOf(
        "idToken" to googleCredential.idToken,
        "email" to extractEmail(googleCredential.idToken),
        "name" to googleCredential.displayName,
        "picture" to googleCredential.profilePictureUri?.toString()
      )
    }
    return null
  }

  private fun resolveServerClientId(): String? {
    val reactContext = appContext.reactContext ?: return null
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
