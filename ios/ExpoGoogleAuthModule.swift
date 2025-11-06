import Foundation
import GoogleSignIn
import React

@objc(ExpoGoogleAuth)
class ExpoGoogleAuthModule: NSObject {
  private var isSigningIn = false

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return true
  }

  @objc(signIn:rejecter:)
  func signIn(_ resolve: @escaping RCTPromiseResolveBlock,
              rejecter reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      self.performSignIn(resolve: resolve, reject: reject)
    }
  }

  private func performSignIn(resolve: @escaping RCTPromiseResolveBlock,
                             reject: @escaping RCTPromiseRejectBlock) {
    guard !isSigningIn else {
      reject("in_progress", "A sign-in request is already running.", nil)
      return
    }

    guard let presentingViewController = RCTPresentedViewController() else {
      reject("no_view_controller", "Unable to find a presenting view controller.", nil)
      return
    }

    guard let clientID = resolveClientID() else {
      reject("missing_client_id", "Set the Google client ID in Info.plist or via the Expo config plugin.", nil)
      return
    }

    let configuration = GIDConfiguration(clientID: clientID)
    isSigningIn = true

    GIDSignIn.sharedInstance.signIn(with: configuration, presenting: presentingViewController) { [weak self] user, error in
      guard let self = self else {
        resolve(nil)
        return
      }

      self.isSigningIn = false

      if let error = error {
        if (error as NSError).code == GIDSignInErrorCode.canceled.rawValue {
          resolve(nil)
          return
        }
        reject("signin_error", error.localizedDescription, error)
        return
      }

      guard let user = user else {
        resolve(nil)
        return
      }

      user.authentication.do { authentication, authError in
        if let authError = authError {
          reject("authentication_error", authError.localizedDescription, authError)
          return
        }

        let result: [String: Any?] = [
          "idToken": authentication?.idToken,
          "accessToken": authentication?.accessToken,
          "email": user.profile?.email,
          "name": user.profile?.name,
          "picture": user.profile?.imageURL(withDimension: 200)?.absoluteString
        ]

        resolve(result.compactMapValues { $0 })
      }
    }
  }

  private func resolveClientID() -> String? {
    if let configured = GIDSignIn.sharedInstance.clientID, !configured.isEmpty {
      return configured
    }

    if let plistValue = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String, !plistValue.isEmpty {
      return plistValue
    }

    return nil
  }
}
