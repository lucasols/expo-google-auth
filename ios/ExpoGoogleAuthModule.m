#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(ExpoGoogleAuth, NSObject)

RCT_EXTERN_METHOD(signIn:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
