/*
Copyright 2014-2016 Intel Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.moe.gradle.task

import org.moe.common.simulator.Simulator
import org.moe.common.simulator.SimulatorManager
import org.moe.common.configuration.ConfigurationValidationException
import org.moe.common.constants.MOEManifestConstants
import org.moe.common.constants.MOEManifestConstants.BUNDLE_RESOURCES
import org.moe.common.constants.MOEManifestConstants.LIBRARIES_PATHS
import org.moe.common.constants.MOEManifestConstants.LINKER_FLAGS
import org.moe.common.utils.OsUtils
import org.moe.common.variant.ArchitectureVariant
import org.moe.common.variant.TargetVariant
import org.moe.generator.project.Generator
import org.moe.generator.project.config.Configuration
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.UnknownTaskException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class XcodeProjectGenerator extends BaseTask {

    static String NAME = "XcodeProjectGenerator"

    static public final String SYM_ROOT = "${BasePlugin.MOE}/xcodebuild/sym"
    static public final String RES_DIR = "resources"

    IdentityHashMap<Enum, List<String>> dependenciesManifestsProperties

    String relativePathToBasePrj = ""
    String relativePathToBuildDir = ""

    String mainClassName

    /*
    Task inputs
     */

    @Input
    @Optional
    Boolean applicationExitOnSuspend = false

    @Input
    @Optional
    String bundleShortVersionString = ""

    @Input
    @Optional
    String bundleVersion = ""

    @Input
    @Optional
    String companyIdentifier = ""

    @Input
    HashSet<File> dependencies = new HashSet<File>()

    @Input
    @Optional
    String deploymentTarget = ""

    @Input
    @Optional
    String gradleVersion = ""

    @Input
    @Optional
    String infoPlistPath = ""

    @Input
    @Optional
    List<String> mainResources

    @Input
    @Optional
    String mainUIStoryboardPath

    @Optional
    @Input
    String launchScreenFilePath = null

    @Optional
    @Input
    String launchImagesSource = null

    @Optional
    @Input
    String locationWhenInUseUsageDescription = null

    @Optional
    @Input
    String appIconsSource = null

    @Input
    @Optional
    String organizationName = ""

    @Input
    @Optional
    String packageName = ""

    @Input
    String productName = ""

    @Input
    String bundleID = ""

    @Input
    String projectName

    @Input
    Boolean skip = false

    @Input
    List<String> supportedInterfaceOrientations = new ArrayList<String>();

    @Input
    String symRoot

    @Input
    String targetPlatform = ""

    @Input
    String testInfoPlistPath = ""

    @Input
    @Optional
    List<String> testResources

    @Input
    boolean useScala = false

    /*
    Task outputs
     */

    @OutputDirectory
    File xcodeProjectDir

    @OutputDirectory
    File librariesDir

    File log

    /*
    Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|> appIconsSource: ${getAppIconsSource()}")
        project.logger.debug("|> applicationExitOnSuspend: ${getApplicationExitOnSuspend()}")
        project.logger.debug("|> bundleID: ${getBundleID()}")
        project.logger.debug("|> bundleShortVersionString: ${getBundleShortVersionString()}")
        project.logger.debug("|> bundleVersion: ${getBundleVersion()}")
        project.logger.debug("|> companyIdentifier: ${getCompanyIdentifier()}")
        project.logger.debug("|> dependencies: ${getDependencies()}")
        project.logger.debug("|> deploymentTarget: ${getDeploymentTarget()}")
        project.logger.debug("|> gradleVersion: ${getGradleVersion()}")
        project.logger.debug("|> infoPlistPath: ${getInfoPlistPath()}")
        project.logger.debug("|> log: ${getLog()}")
        project.logger.debug("|> mainClassName: ${getMainClassName()}")
        project.logger.debug("|> mainResources: ${getMainResources()}")
        project.logger.debug("|> launchScreenFilePath: ${getLaunchScreenFilePath()}")
        project.logger.debug("|> launchImagesSource: ${getLaunchImagesSource()}")
        project.logger.debug("|> locationWhenInUseUsageDescription: ${getLocationWhenInUseUsageDescription()}")
        project.logger.debug("|> organizationName: ${getOrganizationName()}")
        project.logger.debug("|> packageName: ${getPackageName()}")
        project.logger.debug("|> productName: ${getProductName()}")
        project.logger.debug("|> projectName: ${getProjectName()}")
        project.logger.debug("|> skip: ${getSkip()}")
        project.logger.debug("|> storyboardPath: ${getMainUIStoryboardPath()}")
        project.logger.debug("|> supportedInterfaceOrientations: ${getSupportedInterfaceOrientations()}")
        project.logger.debug("|> symRoot: ${getSymRoot()}")
        project.logger.debug("|< targetPlatform: ${getTargetPlatform()}")
        project.logger.debug("|< testInfoPlistPath: ${getTestInfoPlistPath()}")
        project.logger.debug("|< testResources: ${getTestResources()}")
        project.logger.debug("|< useScala: ${getUseScala()}")
        project.logger.debug("|> xcodeProjectDir: ${getXcodeProjectDir().getAbsolutePath()}")

        generateProject()
    }

    private void generateProject() {

        collectDependenciesAttributes(getDependencies())

        if (getSkip()) {
            return
        }
        Configuration conf = new Configuration()

        conf.setApplicationExitOnSuspend(getApplicationExitOnSuspend())
        conf.setBundleShortVersionString(getBundleShortVersionString())
        conf.setBundleVersion(getBundleVersion())
        conf.setDependenciesManifestsProperties(getDependenciesManifestsProperties())
        conf.setDeploymentTarget(getDeploymentTarget())
        conf.setGradleVersion(getGradleVersion())
        conf.setInfoPlistPath(getInfoPlistPath())
        conf.setMainClassName(getMainClassName())
        conf.setMainResourcesSet(getMainResources())
        conf.setMainUIStoryboardPath(getMainUIStoryboardPath())
        conf.setLaunchScreenFilePath(getLaunchScreenFilePath())
        conf.setLaunchImagesSource(getLaunchImagesSource())
        conf.setLocationWhenInUseUsageDescription(getLocationWhenInUseUsageDescription())
        conf.setAppIconsSource(getAppIconsSource())
        conf.setBundleID(getBundleID())
        conf.setOrganizationName(getOrganizationName())
        conf.setPackageName(getPackageName())
        conf.setProductName(getProductName())
        conf.setProjectName(getProjectName())
        conf.setProjectRoot(getXcodeProjectDir())
        conf.setRelativePathToBasePrj(getRelativePathToBasePrj())
        conf.setRelativePathToBuildDir(getRelativePathToBuildDir())
        conf.setSupportedInterfaceOrientations(getSupportedInterfaceOrientations())
        conf.setSymRoot(getSymRoot())
        conf.setTargetPlatform(getTargetPlatform())
        conf.setTestInfoPlistPath(getTestInfoPlistPath())
        conf.setTestResourcesSet(getTestResources())
        conf.setUseScala(getUseScala())

        // Validate settings
        try {
            conf.validate();
        } catch (ConfigurationValidationException ve) {
            throw new GradleException(ve.getPropertyName() + ": " + ve.getErrorMessage());
        }

        // Generate project
        Generator generator = new Generator(conf);
        generator.create();
    }

    static String getMainUIStoryboardPath(final Project project,
                                          final String customStoryboardPath,
                                          final File uiTransformerOutput,
                                          final boolean skipTransformerTask) {
        String result = null;

        boolean isStoryboardPathSet = true;
        if (customStoryboardPath == null || customStoryboardPath.isEmpty()) {
            isStoryboardPathSet = false;
        }

        if (!isStoryboardPathSet) {
            if (!skipTransformerTask) {
                result = uiTransformerOutput.getAbsolutePath().replaceAll("\\\\", "/")
            } else {
                File defaultLocation = project.file("src/main/resources/MainUI.storyboard")
                if (defaultLocation.exists()) {
                    result = defaultLocation.getAbsolutePath().replaceAll("\\\\", "/")
                } else {
                    project.logger.info("Console target, as default storyboard path doesn't exist:" + defaultLocation.getAbsolutePath())
                }
            }
        } else {
            if (!skipTransformerTask) {
                throw new GradleException("Internal error: uiTransformer should not be called if storyboard path isn't empty!")
            } else {
                File customLocation = project.file(customStoryboardPath)
                if (customLocation.exists()) {
                    result = customLocation.getAbsolutePath().replaceAll("\\\\", "/")
                } else {
                    throw new GradleException("Custom storyboard path can't be found here: " + customLocation.getAbsolutePath())
                }
            }
        }

        if (result != null && !result.isEmpty()) {
            String storyboardName
            try {
                storyboardName = result.substring(
                        result.lastIndexOf("/") + 1,
                        result.lastIndexOf(".storyboard"));
            }
            catch (IndexOutOfBoundsException ignored) {
                throw new GradleException("Storyboard name doesn't contain .storyboard extension at path: " + result)
            }
            if ((storyboardName == null) || storyboardName.isEmpty() || !result.endsWith(".storyboard")) {
                throw new GradleException("Storyboard name is incorrect at path: " + result)
            }
        }
        return result;
    }

    public static String getTaskName() {
        return BaseTask.composeTaskName(XcodeProjectGenerator.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = XcodeProjectGenerator.NAME
        final String PATTERN = "${BasePlugin.MOE}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates xcode project."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 0, "")

            XcodeProjectGenerator.create(project, javaConvention.getSourceSets())
        })
    }

    public static XcodeProjectGenerator create(Project project, SourceSetContainer sourceSets) {

        File xcodeProjectDir = getXcodeProjectDirPath(project)
        if (!xcodeProjectDir.exists()) {
            xcodeProjectDir.mkdirs()
        }

        // Create task
        final String taskName = getTaskName()
        XcodeProjectGenerator projectGenerator = project.tasks.create(taskName, XcodeProjectGenerator.class)
        projectGenerator.description = "Generates Xcode project (${xcodeProjectDir.getAbsolutePath()})."

        // Update convention mapping

        Boolean skip = !project.moe.xcode.generateProject
        projectGenerator.conventionMapping.skip = {
            skip
        }

        projectGenerator.conventionMapping.symRoot = {
            project.buildDir.absolutePath + "/" + SYM_ROOT
        }

        String targetName = getXcodeProjectName(project)

        projectGenerator.conventionMapping.projectName = {
            targetName
        }

        projectGenerator.conventionMapping.xcodeProjectDir = {
            xcodeProjectDir
        }

        File libDir = createLibsDirectory(project)
        projectGenerator.conventionMapping.librariesDir = {
            libDir
        }

        projectGenerator.relativePathToBasePrj = Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(project.getProjectDir().getAbsolutePath())).toString().replaceAll("\\\\", "/")
        projectGenerator.relativePathToBuildDir = Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(project.getBuildDir().getAbsolutePath())).toString().replaceAll("\\\\", "/")

        if (!skip) {
            projectGenerator.setProductName(getMainProductName(project))

            projectGenerator.conventionMapping.packageName = {
                project.moe.xcode.packageName
            }
            String mainClassName = project.moe.mainClassName
            if ((mainClassName == null) || mainClassName.isEmpty()) {
                mainClassName = "Main"
            }
            projectGenerator.conventionMapping.mainClassName = {
                mainClassName
            }

            String infoPlistPath = ""
            String relativeInfoPlistPath = project.moe.xcode.infoPlistPath
            // Search custom Info.plist
            if ((relativeInfoPlistPath != null) && !relativeInfoPlistPath.isEmpty()) {
                relativeInfoPlistPath = Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(project.file(relativeInfoPlistPath).getAbsolutePath())).toString()
                if (new File(xcodeProjectDir, relativeInfoPlistPath).exists()) {
                    infoPlistPath = relativeInfoPlistPath.replaceAll("\\\\", "/")
                }
            }
            // Search in default location
            if (infoPlistPath.isEmpty()) {
                relativeInfoPlistPath = projectGenerator.relativePathToBasePrj + '/' + Configuration.DEFAULT_MAIN_INFO_PLIST_PATH + 'Info.plist'
                if (new File(xcodeProjectDir, relativeInfoPlistPath).exists()) {
                    infoPlistPath = relativeInfoPlistPath.replaceAll("\\\\", "/")
                }
            }
            projectGenerator.conventionMapping.infoPlistPath = {
                infoPlistPath
            }

            String testInfoPlistPath = ""
            relativeInfoPlistPath = project.moe.xcode.testInfoPlistPath
            // Search custom Info.plist
            if ((relativeInfoPlistPath != null) && !relativeInfoPlistPath.isEmpty()) {
                relativeInfoPlistPath = Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(project.file(relativeInfoPlistPath).getAbsolutePath())).toString()
                if (new File(xcodeProjectDir, relativeInfoPlistPath).exists()) {
                    testInfoPlistPath = relativeInfoPlistPath.replaceAll("\\\\", "/")
                }
            }
            // Search default location
            if (testInfoPlistPath.isEmpty()) {
                relativeInfoPlistPath = projectGenerator.relativePathToBasePrj + '/' + Configuration.DEFAULT_TEST_INFO_PLIST_PATH + 'Info-Test.plist'
                if (new File(xcodeProjectDir, relativeInfoPlistPath).exists()) {
                    testInfoPlistPath = relativeInfoPlistPath.replaceAll("\\\\", "/")
                }
            }
            projectGenerator.conventionMapping.testInfoPlistPath = {
                testInfoPlistPath
            }

            projectGenerator.conventionMapping.applicationExitOnSuspend = {
                project.moe.xcode.applicationExitOnSuspend
            }

            projectGenerator.conventionMapping.bundleShortVersionString = {
                project.moe.xcode.bundleShortVersionString
            }

            projectGenerator.conventionMapping.bundleVersion = {
                project.moe.xcode.bundleVersion
            }

            projectGenerator.conventionMapping.deploymentTarget = {
                computeDeploymentTarget(project)
            }

            String mainUIStoryboardPath = computeStoryboardPath(sourceSets, project)
            mainUIStoryboardPath = (mainUIStoryboardPath == null) ? "" : mainUIStoryboardPath
            projectGenerator.conventionMapping.mainUIStoryboardPath = {
                mainUIStoryboardPath
            }

            String launchScreenFilePath = computeLaunchScreenFilePath(project)
            projectGenerator.conventionMapping.launchScreenFilePath = {
                launchScreenFilePath
            }

            projectGenerator.conventionMapping.launchImagesSource = {
                project.moe.xcode.launchImagesSource
            }

            projectGenerator.conventionMapping.locationWhenInUseUsageDescription = {
                project.moe.xcode.locationWhenInUseUsageDescription
            }

            projectGenerator.conventionMapping.appIconsSource = {
                project.moe.xcode.appIconsSource
            }


            projectGenerator.conventionMapping.mainResources = {
                collectMainResources(sourceSets, xcodeProjectDir, mainUIStoryboardPath, launchScreenFilePath)
            }

            projectGenerator.conventionMapping.testResources = {
                collectTestResources(sourceSets, xcodeProjectDir)
            }

            List<String> orientations = project.moe.xcode.supportedInterfaceOrientations
            if (orientations != null) {
                projectGenerator.getSupportedInterfaceOrientations().addAll(orientations)
            }

            projectGenerator.conventionMapping.targetPlatform = {
                Configuration.TARGET_PLATFORM_IOS_UNIVERSAL
            }
            projectGenerator.conventionMapping.organizationName = {
                project.moe.xcode.organizationName
            }

            projectGenerator.conventionMapping.companyIdentifier = {
                project.moe.xcode.companyIdentifier
            }

            String bundleID = project.moe.xcode.bundleID
            if ((bundleID == null) || (bundleID.isEmpty())) {
                bundleID = targetName
            }

            projectGenerator.conventionMapping.bundleID = {
                bundleID
            }

            projectGenerator.conventionMapping.gradleVersion = {
                project.getGradle().gradleVersion
            }

            HashSet<File> dependencies = new HashSet<File>();
            for (org.gradle.api.artifacts.Configuration configuration : project.configurations) {
                for (File file : configuration) {
                    if (!dependencies.contains(file)) {
                        dependencies.add(file)
                    }
                }
            }
            projectGenerator.conventionMapping.dependencies = {
                dependencies
            }

            File log = project.file("${project.buildDir.absolutePath}/logs/xcodeProjectGenerator-${new Date().format("yyyy.MM.dd-hh.mm.ss")}.log")
            File logDir = log.getParentFile()
            if ((logDir != null) && !logDir.exists()) {
                logDir.mkdirs()
            }
            log.createNewFile()
            projectGenerator.conventionMapping.log = {
                log
            }
        }

        return projectGenerator
    }

    public static File getXcodeProjectDirPath(Project project) {
        String xcodeProjectDirPath = project.moe.xcode.xcodeProjectDirPath
        if ((xcodeProjectDirPath != null) && xcodeProjectDirPath.isEmpty()) {
            xcodeProjectDirPath = project.projectDir.getAbsolutePath()
        }
        File xcodeProjectDir = (xcodeProjectDirPath == null) ?
                new File(project.getBuildDir(), "xcode") : project.file(xcodeProjectDirPath)
        return xcodeProjectDir
    }

    public static String getXcodeProjectName(Project project) {
        String targetName = project.moe.xcode.mainTarget
        if ((targetName == null) || targetName.isEmpty()) {
            targetName = project.name
        }
        return targetName
    }

    public static String getMainProductName(Project project) {
        String productName = project.moe.xcode.getMainProductName()
        if ((productName == null) || productName.isEmpty()) {
            productName = getXcodeProjectName(project)
        }
        return productName
    }

    private static String computeDeploymentTarget(Project project) {
        String deploymentTarget = project.moe.xcode.deploymentTarget
        if ((deploymentTarget == null || deploymentTarget.equals('')) && OsUtils.isMac()) {
            def simulatorList = SimulatorManager.getSimulators()
            String minSdkVersion
            if (simulatorList.isEmpty()) {
                throw new GradleException("Failed to find iOS simulators! Please install them from Xcode.")
            } else {
                minSdkVersion = simulatorList.get(0).sdk();
                for (Simulator simulator : simulatorList) {
                    if (simulator.sdk().compareToIgnoreCase(minSdkVersion) < 0) {
                        minSdkVersion = simulator.sdk();
                    }
                }
            }
            deploymentTarget = minSdkVersion
        }
        deploymentTarget
    }

    private static String computeLaunchScreenFilePath(Project project) {
        String result = null;
        String launchScreenFilePath = project.moe.xcode.launchScreenFilePath

        if (launchScreenFilePath != null && !launchScreenFilePath.isEmpty()) {
            File customLocation = project.file(launchScreenFilePath)
            if (customLocation.exists()) {
                result = customLocation
            }
        }
        result
    }

    private static String computeStoryboardPath(SourceSetContainer sourceSets, Project project) {
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        File uiTransformerOutput = null
        boolean skipTransformerTask;
        try {
            final String uiTransformerTaskName = UITransformer.getTaskName(mainSourceSet)
            UITransformer uiTransformerTask = (UITransformer) project.tasks.getByName(uiTransformerTaskName)
            uiTransformerOutput = uiTransformerTask.getOutputFile()
            skipTransformerTask = false;
        } catch (UnknownTaskException ignored) {
            skipTransformerTask = true
        }
        String storyboardPath = project.moe.xcode.mainUIStoryboardPath.replaceAll("\\\\", "/")
        return getMainUIStoryboardPath(project, storyboardPath, uiTransformerOutput, skipTransformerTask)
    }

    private
    static List<String> collectMainResources(SourceSetContainer sourceSets, File xcodeProjectDir, String mainUIStoryboardPath, String launchScreenFilePath) {
        Set<String> mainRes = new HashSet<String>()

        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        for (File parent : mainSourceSet.getResources().srcDirs) {
            for (File candidate : parent.listFiles()) {
                for (File child : mainSourceSet.getResources().files) {
                    if (child.toPath().startsWith(candidate.toPath())) {
                        if (child.getName().endsWith(".ixml")) {
                            continue
                        }
                        mainRes.add(Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(candidate.getAbsolutePath())).toString().replaceAll("\\\\", "/"));
                        break;
                    }
                }
            }
        }

        if (mainUIStoryboardPath != null && !mainUIStoryboardPath.empty) {
            mainRes.add(Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(mainUIStoryboardPath)).toString().replaceAll("\\\\", "/"))
        }
        if (launchScreenFilePath != null && !launchScreenFilePath.empty) {
            mainRes.add(Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(launchScreenFilePath)).toString().replaceAll("\\\\", "/"))
        }

        new ArrayList<String>(mainRes)
    }

    private static List<String> collectTestResources(SourceSetContainer sourceSets, File xcodeProjectDir) {
        List<String> testRes = new ArrayList<String>()
        SourceSet testSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
        if (testSet != null) {
            for (File f : testSet.getResources()) {
                testRes.add(Paths.get(xcodeProjectDir.getAbsolutePath()).relativize(Paths.get(f.getAbsolutePath())).toString().replaceAll("\\\\", "/"))
            }
        }
        testRes
    }

    private void collectDependenciesAttributes(HashSet<File> dependencies) {
        dependenciesManifestsProperties = new IdentityHashMap<Enum, List<String>>()

        String resDir = project.getBuildDir().getAbsolutePath() + "/" + RES_DIR;
        File natjRes = new File(resDir);
        if (!natjRes.exists()) {
            natjRes.mkdirs();
        }

        try {
            for (File dependencyFile : dependencies) {

                JarFile jarFile = new JarFile(dependencyFile);
                Manifest manifest = jarFile.getManifest();
                if (manifest == null) {
                    project.logger.warn("File ${dependencyFile.getAbsolutePath()} does not contain manifest file.")
                    continue
                }

                // Add custom linked flags
                for (Enum property : LINKER_FLAGS.values()) {
                    String propertyValue = getManifestAttribute(manifest, property.name());
                    if ((propertyValue != null) && !propertyValue.isEmpty()) {
                        List<String> values = dependenciesManifestsProperties.get(property)
                        if (values == null) {
                            values = new ArrayList<String>()
                            dependenciesManifestsProperties.put(property, values)
                        }
                        for (String value : Arrays.asList(propertyValue.split(";"))) {
                            value = value.replace(";", "").trim()
                            values.add(value)
                        }
                    }
                }

                // Add custom libraries
                String type = getManifestAttribute(manifest, MOEManifestConstants.MOE_TYPE)
                if ((type != null) && !type.isEmpty()) {
                    List<String> typeArray = new ArrayList<String>()
                    typeArray.addAll(Arrays.asList(type.split(";")))

                    File libsDir = createLibsDirectory(project)
                    File dynamicDir = createDynamicDirectory(libsDir)
                    File staticDir = createStaticDirectory(libsDir)

                    Attributes attributes = manifest.getMainAttributes()

                    int typeIndex = 0
                    for (String key : attributes.keySet()) {
                        LIBRARIES_PATHS libPathConst
                        try {
                            libPathConst = LIBRARIES_PATHS.valueOf(key)
                        } catch (IllegalArgumentException e) {
                            continue
                        }

                        String propertyValue;
                        try {
                            propertyValue = attributes.getValue(key)
                        } catch (IllegalArgumentException e) {
                            project.logger.info("Incorrect manifest attribute name '${key}'. ${e.getMassage()}")
                            continue
                        }
                        if ((propertyValue != null) && !propertyValue.isEmpty()) {
                            project.logger.debug("collectDependenciesAttributes: attribute '${key}' with value '${propertyValue}' was found.")
                            List<String> libPaths = dependenciesManifestsProperties.get(libPathConst)
                            if (libPaths == null) {
                                libPaths = new ArrayList<String>()
                                dependenciesManifestsProperties.put(libPathConst, libPaths)
                            }

                            for (String path : Arrays.asList(propertyValue.split(";"))) {
                                path = path.replace(";", "").trim()

                                List<String> subPaths = new ArrayList<String>()
                                if (libPathConst != LIBRARIES_PATHS.MOE_ThirdpartyFramework_universal) {
                                    List<TargetVariant> targets = new ArrayList<TargetVariant>();
                                    TargetVariant targetVariant = TargetVariant.getByManifestProperty(libPathConst)
                                    if (targetVariant == null) {
                                        ArchitectureVariant archVariant = ArchitectureVariant.getByManifestProperty(libPathConst)
                                        if (archVariant != null) {
                                            targets = addAll(TargetVariant.getSupportedTargetVariants(archVariant))
                                        }
                                    } else {
                                        targets.add(targetVariant)
                                    }
                                    for (TargetVariant target : targets) {
                                        subPaths.add(target.getPlatformName())
                                    }
                                } else {
                                    subPaths.add("")
                                }

                                type = typeArray.get(typeIndex).replace(";", "").trim()

                                File dstDir
                                if (type.equals("dynamic")) {
                                    dstDir = dynamicDir
                                } else {
                                    dstDir = staticDir
                                }

                                for (String subPath : subPaths) {
                                    File destDir = new File(dstDir, subPath)
                                    if (!destDir.exists()) {
                                        destDir.mkdirs()
                                    }
                                    copyFromJar(jarFile, path, destDir)
                                    String libName = new File(path).getName()
                                    libPaths.add(destDir.getAbsolutePath() + "/" + libName)
                                    project.logger.debug("Lib '${libName}' was placed to ${destDir.getAbsolutePath()}")
                                }

                                typeIndex++;
                            }
                        }
                    }
                }

                // Add bundle resources
                for (BUNDLE_RESOURCES resFlag : BUNDLE_RESOURCES.values()) {
                    String bundleRes = getManifestAttribute(manifest, resFlag.name());
                    if (bundleRes != null && !bundleRes.isEmpty()) {
                        String[] bundleArray = bundleRes.split(";");

                        List<String> values = dependenciesManifestsProperties.get(resFlag)
                        if (values == null) {
                            values = new ArrayList<String>()
                            dependenciesManifestsProperties.put(resFlag, values)
                        }

                        for (String bundlePath : bundleArray) {
                            bundlePath = bundlePath.replace(";", "").trim();

                            copyFromJar(jarFile, bundlePath, natjRes);
                            values.add(RES_DIR + "/" + new File(bundlePath).getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private String getManifestAttribute(Manifest manifest, String attribute) {
        String retValue;
        Attributes attributes = manifest.getMainAttributes()
        try {
            retValue = attributes.getValue(attribute)
        } catch (IllegalArgumentException e) {
            project.logger.info("Incorrect manifest attribute name '${attribute}'. ${e.getMassage()}")
        }
        return retValue
    }

    private void copyFromJar(ZipFile zipFile, String relativeFilePath, File destination) throws IOException {
        Enumeration<? extends ZipEntry> e = zipFile.entries()
        InputStream is = null
        FileOutputStream fStream = null
        if (relativeFilePath.startsWith("./")) {
            relativeFilePath = relativeFilePath.substring(2)
        }
        int startIndex = 0
        File relativeFile = new File(relativeFilePath)
        if (relativeFile.getParentFile() != null) {
            startIndex = relativeFile.getParent().length() + 1
        }

        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();

            // if the entry is not directory and matches relative file then extract it
            String entryName = entry.getName()
            if (entryName.startsWith(relativeFilePath)) {
                if (entry.isDirectory()) {
                    String dest = entry.getName().substring(startIndex)
                    if (!dest.isEmpty()) {
                        File destFolder = new File(destination, dest)
                        if (!destFolder.exists()) {
                            destFolder.mkdirs()
                        }
                    }
                } else {
                    String dest = entry.getName().substring(startIndex)
                    if (!dest.isEmpty()) {
                        File destFile = new File(destination, dest)
                        if (!destFile.exists()) {
                            destFile.createNewFile()
                        }
                        try {
                            is = zipFile.getInputStream(entry); // get the input stream
                            fStream = new FileOutputStream(destFile)
                            copyFiles(is, fStream)
                        } finally {
                            if (is != null) {
                                try {
                                    is.close();
                                } catch (IOException ioe) {
                                    ioe.printStackTrace()
                                }
                            }
                            if (fStream != null) {
                                try {
                                    fStream.close();
                                } catch (IOException ioe) {
                                    ioe.printStackTrace()
                                }
                            }
                        }
                    }
                }
            } else {
                continue
            }
        }
    }

    private void copyFiles(InputStream inStream, FileOutputStream outStream) throws IOException {
        byte[] buffer = new byte[1024]

        int length;
        while ((length = inStream.read(buffer)) > 0) {
            outStream.write(buffer, 0, length)
        }

    }

    public static File createLibsDirectory(Project project) {
        File tempDir = new File(project.getBuildDir(), "libs")

        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        return tempDir
    }

    public File createDynamicDirectory(File libs) {
        File dynamicDir = new File(libs, "dynamic");
        if (!dynamicDir.exists()) {
            dynamicDir.mkdirs()
        }
        for (TargetVariant variant : TargetVariant.getAll()) {
            File targetDir = new File(dynamicDir, variant.getPlatformName())
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
        }

        return dynamicDir
    }

    public File createStaticDirectory(File libs) {
        File staticDir = new File(libs, "static")
        if (!staticDir.exists()) {
            staticDir.mkdirs()
        }
        for (TargetVariant variant : TargetVariant.getAll()) {
            File targetDir = new File(staticDir, variant.getPlatformName())
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
        }

        return staticDir
    }
}
