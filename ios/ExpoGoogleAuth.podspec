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
  s.source_files = '*.swift'
  s.requires_arc = true
  s.platform     = :ios, '15.1'
  s.swift_version = '5.0'

  s.dependency 'ExpoModulesCore'
  # 7.1+ ships the privacy manifest Apple requires for App Store submissions
  s.dependency 'GoogleSignIn', '>= 7.1', '< 11'
end
