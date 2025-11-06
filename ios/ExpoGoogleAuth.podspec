require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name         = 'ExpoGoogleAuth'
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']
  s.author       = package['author']
  s.homepage     = 'https://github.com/lucasoliveirasantos/expo-google-auth'
  s.source       = { :git => 'https://github.com/lucasoliveirasantos/expo-google-auth.git', :tag => "v#{s.version}" }
  s.source_files = 'ExpoGoogleAuthModule.{swift,m}'
  s.requires_arc = true
  s.platform     = :ios, '13.0'
  s.swift_version = '5.0'

  s.dependency 'React-Core'
  s.dependency 'GoogleSignIn'
end
