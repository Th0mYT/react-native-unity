
require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))
folly_compiler_flags = '-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1 -Wno-comma -Wno-shorten-64-to-32'

Pod::Spec.new do |s|
  s.name         = "react-native-unity"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "12.4" }
  s.source       = { :git => "https://github.com/azesmway/react-native-unity.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm}"

  # Use install_modules_dependencies helper to install the dependencies if React Native version >=0.71.0.
  # See https://github.com/facebook/react-native/blob/febf6b7f33fdb4904669f99d795eba4c0f95d7bf/scripts/cocoapods/new_architecture.rb#L79.
  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency "React-Core"

    # Don't install the dependencies when we run `pod install` in the old architecture.
    if ENV['RCT_NEW_ARCH_ENABLED'] == '1' then
      s.compiler_flags = folly_compiler_flags + " -DRCT_NEW_ARCH_ENABLED=1"
      s.pod_target_xcconfig    = {
          "DEFINES_MODULE" => "YES",
          "HEADER_SEARCH_PATHS" => "\"$(PODS_ROOT)/boost\"",
          "OTHER_CPLUSPLUSFLAGS" => "-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1",
          "CLANG_CXX_LANGUAGE_STANDARD" => "c++17"
      }
      s.dependency "React-RCTFabric"
      s.dependency "React-Codegen"
      s.dependency "RCT-Folly"
      s.dependency "RCTRequired"
      s.dependency "RCTTypeSafety"
      s.dependency "ReactCommon/turbomodule/core"
    end
  end

  # Determine the correct Unity framework path
  # For Expo projects, the framework is at the project root level
  # For React Native CLI projects, it's relative to node_modules
  project_root = File.expand_path("../..", __dir__)
  unity_framework_path = File.join(project_root, "unity/builds/ios/UnityFramework.framework")

  # Check if we're in an Expo project by looking for app.json with expo config
  is_expo_project = File.exist?(File.join(project_root, "app.json")) &&
                    File.read(File.join(project_root, "app.json")).include?('"expo"')

  if is_expo_project
    # Expo: Framework is at project root
    s.vendored_frameworks = [unity_framework_path]
    s.xcconfig = {
      'FRAMEWORK_SEARCH_PATHS' => '"$(PODS_ROOT)/../../unity/builds/ios"'
    }
    s.preserve_paths = unity_framework_path
  else
    # React Native CLI: Use prepare_command to copy framework
    s.prepare_command = <<-CMD
      cp -R ../../../unity/builds/ios/ ios/
    CMD
    s.vendored_frameworks = ["ios/UnityFramework.framework"]
  end

  # Preserve the framework path for both cases
  s.user_target_xcconfig = {
    'FRAMEWORK_SEARCH_PATHS' => '"$(PODS_ROOT)/../../unity/builds/ios" $(inherited)'
  }
end
