module.exports = {
  dependency: {
    platforms: {
      ios: {
        podspecPath: 'ios/ExpoGoogleAuth.podspec',
      },
      android: {
        packageImportPath: 'import com.lsstack.expogoogleauth.ExpoGoogleAuthPackage;',
        packageInstance: 'new ExpoGoogleAuthPackage()',
      },
    },
  },
};
