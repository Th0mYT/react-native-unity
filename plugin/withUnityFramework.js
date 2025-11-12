const { withXcodeProject } = require('@expo/config-plugins');

function withUnityFramework(config) {
  return withXcodeProject(config, async (config) => {
    const xcodeProject = config.modResults;

    // Add framework search paths
    const frameworkSearchPaths = '"$(SRCROOT)/../unity/builds/ios"';
    xcodeProject.addToBuildSettings('FRAMEWORK_SEARCH_PATHS', frameworkSearchPaths);

    return config;
  });
}

module.exports = withUnityFramework;