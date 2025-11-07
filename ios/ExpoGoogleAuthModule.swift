import ExpoModulesCore
import Foundation
import GoogleSignIn

public class ExpoGoogleAuthModule: Module {
  private enum NativeError: String {
    case inProgress = "in_progress"
    case noViewController = "no_view_controller"
    case missingClientID = "missing_client_id"
    case authentication = "authentication_error"
    case signIn = "signin_error"
  }

  private enum Constants {
    static let infoPlistClientIDKey = "GIDClientID"
    static let legacyClientIDKey = "CLIENT_ID"
    static let googleServiceInfoName = "GoogleService-Info"
    static let googleSignInErrorDomain = "com.google.GIDSignIn"
    static let profileImageDimension: UInt = 200
  }

  private enum SignInErrorCode: Int {
    case unknown = -1
    case keychain = -2
    case hasNoAuthInKeychain = -4
    case canceled = -5
    case emm = -6
    case scopesAlreadyGranted = -8
    case mismatchWithCurrentUser = -9
  }

  private var isSigningIn = false

  public func definition() -> ModuleDefinition {
    Name("ExpoGoogleAuth")

    AsyncFunction("signIn") { [weak self] (promise: Promise) in
      guard let self = self else {
        promise.resolve(nil)
        return
      }

      DispatchQueue.main.async {
        self.performSignIn(promise: promise)
      }
    }
  }

  private func performSignIn(promise: Promise) {
    guard !isSigningIn else {
      promise.reject(NativeError.inProgress.rawValue, "A Google sign-in request is already running.")
      return
    }

    guard let presentingViewController = appContext?.utilities?.currentViewController() else {
      promise.reject(NativeError.noViewController.rawValue, "Unable to find a presenting view controller.")
      return
    }

    guard let configuration = buildConfiguration() else {
      promise.reject(NativeError.missingClientID.rawValue, "Set the Google client ID in Info.plist or via the Expo config plugin.")
      return
    }

    isSigningIn = true
    GIDSignIn.sharedInstance.configuration = configuration

    GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController, hint: nil, additionalScopes: nil) { [weak self] result, error in
      guard let self = self else {
        promise.resolve(nil)
        return
      }

      self.isSigningIn = false
      self.handleSignInResult(result: result, error: error, promise: promise)
    }
  }

  private func buildConfiguration() -> GIDConfiguration? {
    guard let clientID = resolveClientID() else {
      return nil
    }

    return GIDConfiguration(clientID: clientID)
  }

  private func resolveClientID() -> String? {
    if let configured = GIDSignIn.sharedInstance.configuration?.clientID, !configured.isEmpty {
      return configured
    }

    if let infoValue = bundleClientIDValue(forKey: Constants.infoPlistClientIDKey) {
      return infoValue
    }

    if let legacyValue = bundleClientIDValue(forKey: Constants.legacyClientIDKey) {
      return legacyValue
    }

    return googleServicePlistClientID()
  }

  private func bundleClientIDValue(forKey key: String) -> String? {
    guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
          !value.isEmpty else {
      return nil
    }

    return value
  }

  private func googleServicePlistClientID() -> String? {
    guard let url = Bundle.main.url(forResource: Constants.googleServiceInfoName, withExtension: "plist"),
          let data = try? Data(contentsOf: url),
          let plist = try? PropertyListSerialization.propertyList(from: data, options: [], format: nil),
          let dictionary = plist as? [String: Any],
          let clientID = dictionary[Constants.legacyClientIDKey] as? String,
          !clientID.isEmpty else {
      return nil
    }

    return clientID
  }

  private func handleSignInResult(result: GIDSignInResult?,
                                  error: Error?,
                                  promise: Promise) {
    if let error = error as NSError? {
      if isCancellationError(error) {
        promise.resolve(nil)
        return
      }

      rejectWithSignInError(error, promise: promise)
      return
    }

    guard let user = result?.user else {
      promise.reject(NativeError.signIn.rawValue, "Google sign-in did not return a user.")
      return
    }

    guard let idToken = user.idToken?.tokenString, !idToken.isEmpty else {
      promise.reject(NativeError.authentication.rawValue, "Google sign-in failed to provide an ID token.")
      return
    }

    promise.resolve(buildPayload(from: user, idToken: idToken))
  }

  private func buildPayload(from user: GIDGoogleUser, idToken: String) -> [String: Any] {
    var payload: [String: Any] = ["idToken": idToken]

    let accessTokenValue = user.accessToken.tokenString
    if !accessTokenValue.isEmpty {
      payload["accessToken"] = accessTokenValue
    }

    if let email = user.profile?.email, !email.isEmpty {
      payload["email"] = email
    }

    if let name = user.profile?.name, !name.isEmpty {
      payload["name"] = name
    }

    if user.profile?.hasImage == true,
       let url = user.profile?.imageURL(withDimension: Constants.profileImageDimension)?.absoluteString,
       !url.isEmpty {
      payload["picture"] = url
    }

    return payload
  }

  private func isCancellationError(_ error: NSError) -> Bool {
    if error.domain == Constants.googleSignInErrorDomain &&
      error.code == SignInErrorCode.unknown.rawValue &&
      error.localizedDescription == "access_denied" {
      return true
    }

    return error.domain == Constants.googleSignInErrorDomain &&
      error.code == SignInErrorCode.canceled.rawValue
  }

  private func rejectWithSignInError(_ error: NSError,
                                     promise: Promise) {
    let message: String

    switch SignInErrorCode(rawValue: error.code) {
    case .keychain:
      message = "A problem reading or writing to the application keychain."
    case .hasNoAuthInKeychain:
      message = "The user has never signed in before or has since signed out."
    case .canceled:
      message = "The user canceled the sign in request."
    case .emm:
      message = "An Enterprise Mobility Management related error has occurred."
    case .scopesAlreadyGranted:
      message = "The requested scopes have already been granted to the current user."
    case .mismatchWithCurrentUser:
      message = "There was an operation on a previous user."
    default:
      message = "Unknown error in Google sign in."
    }

    let formattedMessage = "ExpoGoogleAuth: \(message) (\(error.localizedDescription))"
    promise.reject(NativeError.signIn.rawValue, formattedMessage)
  }
}
