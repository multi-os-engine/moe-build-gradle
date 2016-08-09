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

package org.moe.gradle

import org.moe.common.sdk.MOESDK
import org.moe.common.utils.OsUtils
import org.moe.common.variant.ArchitectureVariant
import org.moe.common.variant.ModeVariant
import org.moe.common.variant.TargetVariant
import org.moe.gradle.task.*
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.moe.gradle.util.XcodeUtil
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

abstract class BasePlugin extends AbstractPlugin implements Plugin<Project> {

    public static final String MOE = 'moe'

    public static final String RESOURCE_PACKAGER_TASK_NAME = 'ResourcePackager'

    public static final String XCODE_INTERNAL_TASK_NAME = 'XcodeInternal'

    protected Instantiator instantiator

    private BaseExtension extension

    @Inject
    public BasePlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    protected abstract Class<? extends BaseExtension> getExtensionClass()

    public BaseExtension getExtension() {
        return extension
    }

    public void apply(Project project) {
        super.apply(project)

        if (OsUtils.mac) {
            XcodeUtil.get().initialize(project)
        }

        // Create plugin extension
        extension = project.extensions.create(MOE, getExtensionClass(), this, (ProjectInternal) project,
                instantiator)

        // Get Java convention
        JavaPluginConvention javaConvention = (JavaPluginConvention) project.getConvention().getPlugins().get("java")

        String home = MOESDK.getHomePath()
        project.getDependencies().add(JavaPlugin.COMPILE_CONFIGURATION_NAME, new SimpleFileCollection(new File(home, "sdk/moe-ios.jar")))
        project.getDependencies().add(JavaPlugin.COMPILE_CONFIGURATION_NAME, new SimpleFileCollection(new File(home, "sdk/moe-core.jar")))
        File junit = new File(home, "sdk/moe-ios-junit.jar")
        if (junit.exists()) {
            project.getDependencies().add(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, new SimpleFileCollection(junit))
        }

        // Configure rules
        configureTaskRules(project, javaConvention)

        // Add lifecycle task dependency
        Task buildTask = project.tasks.getByName("build")
        if (buildTask != null) {
            buildTask.dependsOn lifecycleTask
        }

        Task xcodeProperties = project.tasks.create("moeXcodeProperties") << {
            println "moe.xcode.mainTarget:${extension.xcode.mainTarget}"
            println "moe.xcode.testTarget:${extension.xcode.testTarget}"
            println "moe.xcode.mainProductName:${extension.xcode.mainProductName}"
            println "moe.xcode.testProductName:${extension.xcode.testProductName}"
        }

        project.tasks.create("moeIpaProperties") << {
            println "moe.ipaOptions.provisioningProfile:${extension.ipaOptions.provisioningProfile}"
            println "moe.ipaOptions.signingIdentity:${extension.ipaOptions.signingIdentity}"
        }

        project.tasks.create("moeXcodeProjectPath") << {
            println "moe.xcode.xcodeProjectPath:${XcodeProjectGenerator.getXcodeProjectDirPath(project).getAbsolutePath()}/${XcodeProjectGenerator.getXcodeProjectName(project)}.xcodeproj"
        }

        project.tasks.create("moeMainProductName") << {
            println "moe.xcode.mainProductName:${XcodeProjectGenerator.getMainProductName(project)}"
        }

        configureTargetSupport()
    }

    void configureTargetSupport() {
        Task listDevices = project.tasks.create("moeListDevices") << {
            println "Connected iOS Devices: ${DeviceTestRunner.listUDIDs()}"
        }

        Task listSimulators = project.tasks.create("moeListSimulators") << {
            println "Available iOS Simulators:"
            SimulatorAppLauncher.getListSimulators().each{
                println it
            }

        }

        final String DEPLOY_PREFIX = "moeDeploy"
        final String DEVICE_DEPLOY_NAME = "${DEPLOY_PREFIX}Device"
        final String LAUNCH_PREFIX = "moeLaunch"
        final String DEVICE_LAUNCH_NAME = "${LAUNCH_PREFIX}Device"
        final String SIMULATOR_LAUNCH_NAME = "${LAUNCH_PREFIX}Simulator"
        project.tasks.addRule("MOE deployer and launcher tasks: $DEVICE_DEPLOY_NAME" +
                ", $DEVICE_LAUNCH_NAME, $SIMULATOR_LAUNCH_NAME",
                { String taskName ->
                    project.logger.info("Evaluating rule: $taskName")

                    // Prefix or suffix failed
                    final Class testRunnerClass
                    final String targetVariant
                    final String modeVariant = ModeVariant.RELEASE_NAME
                    final String targetPropertyName
                    final String debugPropertyName
                    final String globalTargetPropertyName
                    final String globalDebugPropertyName
                    final String logFilePropertyName
                    final String enableAndroidLogsPropertyName
                    if (DEVICE_DEPLOY_NAME.equals(taskName)) {
                        testRunnerClass = DeviceAppLauncher.class
                        if (project.moe.sdk.isIOS()) {
                            targetVariant = TargetVariant.IOS_DEVICE_PLATFORM_NAME
                        } else if (project.moe.sdk.isTvOS()) {
                            targetVariant = TargetVariant.TVOS_DEVICE_PLATFORM_NAME
                        } else {
                            throw new GradleException("unsupported platform for task " + taskName)
                        }
                        targetPropertyName = "moe.deploy.device.target"
                        debugPropertyName = null
                        globalTargetPropertyName = "moe.device.target"
                        globalDebugPropertyName = "moe.device.debug"
                        logFilePropertyName = "moe.device.log.file"
                        enableAndroidLogsPropertyName = "moe.android.logs"

                    } else if (DEVICE_LAUNCH_NAME.equals(taskName)) {
                        testRunnerClass = DeviceAppLauncher.class
                        if (project.moe.sdk.isIOS()) {
                            targetVariant = TargetVariant.IOS_DEVICE_PLATFORM_NAME
                        } else if (project.moe.sdk.isTvOS()) {
                            targetVariant = TargetVariant.TVOS_DEVICE_PLATFORM_NAME
                        } else {
                            throw new GradleException("unsupported platform for task " + taskName)
                        }
                        targetPropertyName = "moe.launch.device.target"
                        debugPropertyName = "moe.launch.device.debug"
                        globalTargetPropertyName = "moe.device.target"
                        globalDebugPropertyName = "moe.device.debug"
                        logFilePropertyName = "moe.device.log.file"
                        enableAndroidLogsPropertyName = "moe.android.logs"

                    } else if (SIMULATOR_LAUNCH_NAME.equals(taskName)) {
                        testRunnerClass = SimulatorAppLauncher.class
                        if (project.moe.sdk.isIOS()) {
                            targetVariant = TargetVariant.IOS_SIMULATOR_PLATFORM_NAME
                        } else if (project.moe.sdk.isTvOS()) {
                            targetVariant = TargetVariant.TVOS_SIMULATOR_PLATFORM_NAME
                        } else {
                            throw new GradleException("unsupported platform for task " + taskName)
                        }
                        targetPropertyName = "moe.launch.simulator.target"
                        debugPropertyName = "moe.launch.simulator.debug"
                        globalTargetPropertyName = "moe.simulator.target"
                        globalDebugPropertyName = "moe.simulator.debug"
                        logFilePropertyName = "moe.simulator.log.file"
                        enableAndroidLogsPropertyName = "moe.android.logs"

                    } else {
                        return false
                    }

                    // Create and configure launcher task
                    AbstractAppLauncher launcherTask = project.tasks.create(taskName, testRunnerClass)
                    if (DEVICE_DEPLOY_NAME.equals(taskName)) {
                        launcherTask.installOnly = true
                    }
                    if (debugPropertyName != null && project.hasProperty(debugPropertyName)) {
                        launcherTask.debug = true
                        String value = project.properties[debugPropertyName]
                        if (value != null && value instanceof String && value.length() > 0) {
                            launcherTask.debugPort = value
                        }
                    } else if (debugPropertyName != null && project.hasProperty(globalDebugPropertyName)) {
                        launcherTask.debug = true
                        String value = project.properties[globalDebugPropertyName]
                        if (value != null && value instanceof String && value.length() > 0) {
                            launcherTask.debugPort = value
                        }
                    }

                    // Add build task as dependency
                    def xcodeDeviceBuildTaskName = createTaskName(SourceSet.MAIN_SOURCE_SET_NAME,
                            modeVariant, targetVariant, XcodeBuild.NAME)
                    def xcodeDeviceBuildTask = project.tasks.getByName(xcodeDeviceBuildTaskName)
                    launcherTask.dependsOn xcodeDeviceBuildTask

                    // Construct default output path
                    String outPath = "${MOE}/reports/launch-${targetVariant}"
                    launcherTask.conventionMapping.appFile = {
                        String mainProductName = extension.xcode.getMainProductName()
                        if ((mainProductName == null) || mainProductName.isEmpty()) {
                            mainProductName = project.getName()
                        }
                        String appPath = "$modeVariant-$targetVariant/${mainProductName}.app"
                        new File((File) xcodeDeviceBuildTask.getSymRoot(), appPath)
                    }

                    File logFilePath
                    if (project.hasProperty(logFilePropertyName)) {
                        logFilePath = project.file(project.property(logFilePropertyName))
                    } else {
                        logFilePath = project.file("${project.buildDir}/${outPath}/task.log")
                    }
                    launcherTask.conventionMapping.log = {
                        logFilePath
                    }

                    if ((enableAndroidLogsPropertyName != null) && project.hasProperty(enableAndroidLogsPropertyName)) {
                        boolean enableAndroidLogs = Boolean.getBoolean(project.property(enableAndroidLogsPropertyName))
                        launcherTask.conventionMapping.enableAndroidLog = {
                            enableAndroidLogs
                        }
                    }

                    // Configure targets
                    String targetNameList = null
                    if (project.hasProperty(targetPropertyName)) {
                        targetNameList = project.property(targetPropertyName)
                    } else if (project.hasProperty(globalTargetPropertyName)) {
                        targetNameList = project.property(globalTargetPropertyName)
                    }
                    if (targetNameList != null) {
                        Collection<String> targets = TaskUtil.valuesFromCSV(targetNameList, targetPropertyName)
                        targets = targets.collect { it.trim() }.findAll { it.length() > 0 }
                        if (!DEVICE_DEPLOY_NAME.equals(taskName) && targets.size() > 1) {
                            throw new GradleException("launch tasks can only have one target")
                        }
                        launcherTask.conventionMapping.targets = {
                            targets
                        }
                    }
                })

        final String RUN_TEST_PREFIX = "moeRunTests"
        final String DEVICE_TEST_NAME = "${RUN_TEST_PREFIX}Device"
        final String SIMULATOR_TEST_NAME = "${RUN_TEST_PREFIX}Simulator"
        project.tasks.addRule("MOE test runner tasks: $DEVICE_TEST_NAME, $SIMULATOR_TEST_NAME",
                { String taskName ->
                    project.logger.info("Evaluating rule: $taskName")

                    // Prefix or suffix failed
                    final Class testRunnerClass
                    final String targetVariant
                    final String modeVariant = ModeVariant.RELEASE_NAME
                    final String targetPropertyName
                    final String debugPropertyName
                    final String globalTargetPropertyName
                    final String globalDebugPropertyName
                    if (DEVICE_TEST_NAME.equals(taskName)) {
                        testRunnerClass = DeviceTestRunner.class
                        if (project.moe.sdk.isIOS()) {
                            targetVariant = TargetVariant.IOS_DEVICE_PLATFORM_NAME
                        } else if (project.moe.sdk.isTvOS()) {
                            targetVariant = TargetVariant.TVOS_DEVICE_PLATFORM_NAME
                        } else {
                            throw new GradleException("unsupported platform for task " + taskName)
                        }
                        targetPropertyName = "moe.test.device.target"
                        debugPropertyName = "moe.test.device.debug"
                        globalTargetPropertyName = "moe.device.target"
                        globalDebugPropertyName = "moe.device.debug"

                    } else if (SIMULATOR_TEST_NAME.equals(taskName)) {
                        testRunnerClass = SimulatorTestRunner.class
                        if (project.moe.sdk.isIOS()) {
                            targetVariant = TargetVariant.IOS_SIMULATOR_PLATFORM_NAME
                        } else if (project.moe.sdk.isTvOS()) {
                            targetVariant = TargetVariant.TVOS_SIMULATOR_PLATFORM_NAME
                        } else {
                            throw new GradleException("unsupported platform for task " + taskName)
                        }
                        targetPropertyName = "moe.test.simulator.target"
                        debugPropertyName = "moe.test.simulator.debug"
                        globalTargetPropertyName = "moe.simulator.target"
                        globalDebugPropertyName = "moe.simulator.debug"

                    } else if (RUN_TEST_PREFIX.equals(taskName)) {
                        Task tests = project.tasks.create(RUN_TEST_PREFIX)

                        DeviceTestRunner devTests = project.tasks.getByName(DEVICE_TEST_NAME)
                        tests.dependsOn devTests
                        SimulatorTestRunner simTests = project.tasks.getByName(SIMULATOR_TEST_NAME)
                        tests.dependsOn simTests

                        devTests.dependsOn simTests
                        simTests.skipThrowOnFail()
                        return true;

                    } else {
                        return false;
                    }

                    // Create and configure launcher task
                    AbstractTestRunner runTestsTask = project.tasks.create(taskName, testRunnerClass)
                    if (debugPropertyName != null && project.hasProperty(debugPropertyName)) {
                        runTestsTask.debug = true
                        String value = project.properties[debugPropertyName]
                        if (value != null && value instanceof String && value.length() > 0) {
                            runTestsTask.debugPort = value
                        }
                    } else if (debugPropertyName != null && project.hasProperty(globalDebugPropertyName)) {
                        launcherTask.debug = true
                        String value = project.properties[globalDebugPropertyName]
                        if (value != null && value instanceof String && value.length() > 0) {
                            launcherTask.debugPort = value
                        }
                    }

                    // Add build task as dependency
                    def xcodeBuildTaskName = createTaskName(SourceSet.TEST_SOURCE_SET_NAME,
                            modeVariant, targetVariant, XcodeBuild.NAME)
                    def xcodeBuildTask = project.tasks.getByName(xcodeBuildTaskName)
                    runTestsTask.dependsOn xcodeBuildTask

                    // Construct default output path
                    String outPath = "${MOE}/reports/junit-${targetVariant}"
                    runTestsTask.conventionMapping.appFile = {
                        String appPath = "$modeVariant-$targetVariant/${extension.xcode.testProductName}.app"
                        new File((File) xcodeBuildTask.getSymRoot(), appPath)
                    }
                    runTestsTask.conventionMapping.reportsDir = {
                        project.file("${project.buildDir}/${outPath}")
                    }
                    runTestsTask.conventionMapping.log = {
                        project.file("${project.buildDir}/${outPath}/task.log")
                    }

                    String targetNameList = null
                    if (project.hasProperty(targetPropertyName)) {
                        targetNameList = project.property(targetPropertyName)
                    } else if (project.hasProperty(globalTargetPropertyName)) {
                        targetNameList = project.property(globalTargetPropertyName)
                    }
                    if (targetNameList != null) {
                        Collection<String> targets = TaskUtil.valuesFromCSV(targetNameList, targetPropertyName)
                        targets = targets.collect { it.trim() }.findAll { it.length() > 0 }
                        runTestsTask.conventionMapping.targets = {
                            targets
                        }
                    }
                })
    }

    private void configureTaskRules(Project project, JavaPluginConvention javaConvention) {
        ProGuard.addRule(project, javaConvention)
        Retrolambda.addRule(project, javaConvention)
        configureResourcePackagerRule(project, javaConvention)
        OutsidePackager.addRule(project, javaConvention)
        StartupProvider.addRule(project, javaConvention)
        TestClassesProvider.addRule(project, javaConvention)
        UITransformer.addRule(project, javaConvention)
        Dex.addRue(project, javaConvention)
        Dex2Oat.addRule(project, javaConvention)
        XcodeProvider.addRule(project, javaConvention)
        configureXcodeInternalRule(project, javaConvention)
        XcodeProjectGenerator.addRule(project, javaConvention)
        XcodeBuild.addRule(project, javaConvention)
        IpaBuild.addRule(project, javaConvention)
    }

    private void configureResourcePackagerRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = RESOURCE_PACKAGER_TASK_NAME
        final String ELEMENTS_DESC = '<SourceSet>'

        // Add rule
        project.tasks.addRule("Pattern: ${MOE}${ELEMENTS_DESC}${TASK_NAME}: Creates a application resource jar."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return false
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 1, ELEMENTS_DESC)

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
            configureResourcePackagerTask(sourceSet)
        })
    }

    private void configureXcodeInternalRule(Project project, JavaPluginConvention javaConvention) {
        // Simple check whether this was called from inside Xcode or not
        if (System.getenv("XCODE_PRODUCT_BUILD_VERSION") == null) {
            return
        }

        // Prepare constants
        final String TASK_NAME = XCODE_INTERNAL_TASK_NAME

        // Add rule
        project.tasks.addRule("Pattern: ${MOE}${TASK_NAME}: Creates all object files for Xcode."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return false
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 0, "")

            // Check element values & configure task on success
            configureXcodeInternalTask(javaConvention)
        })
    }

    public Jar configureResourcePackagerTask(SourceSet sourceSet) {
        // Collect settings
        final String sourceSetName = sourceSet.name

        // Construct default output path
        final String outPath = "${MOE}/${sourceSet.name}"

        // Create task
        final String taskName = createTaskName(sourceSet.name, RESOURCE_PACKAGER_TASK_NAME)
        Jar resourcePackagerTask = project.tasks.create(taskName, Jar.class)
        resourcePackagerTask.description = "Generates application file ($outPath)."
        resourcePackagerTask.duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // Add dependencies
        final String proguardTaskName = ProGuard.getTaskName(sourceSet)
        ProGuard proguardTask = (ProGuard) project.tasks.getByName(proguardTaskName)
        resourcePackagerTask.dependsOn proguardTask

        // Update settings
        resourcePackagerTask.setDestinationDir(project.file("${project.buildDir}/${outPath}"))
        resourcePackagerTask.setArchiveName("application.jar")
        resourcePackagerTask.from(project.zipTree(proguardTask.getOutJar()))

        if (!extension.sdk.getFullTrim()) {
            // When using full trim, ProGuard will copy the the resources from the common jar
            extension.sdk.getMainJars().files.each {
                resourcePackagerTask.from(project.zipTree(it))
            }
        }
        resourcePackagerTask.exclude("**/*.class")
        if (extension.packagingOptions.excludes != null) {
            extension.packagingOptions.excludes.each {
                resourcePackagerTask.exclude(it)
            }
        }

        // Add support for copying resources from the source directory
        if (!extension.mavenProject) {
            addResourceFromSources(resourcePackagerTask, sourceSet)
            if (sourceSetName == SourceSet.TEST_SOURCE_SET_NAME) {
                JavaPluginConvention javaConvention = (JavaPluginConvention) project.getConvention().getPlugins().get("java")
                def main = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                addResourceFromSources(resourcePackagerTask, main)
            }
        }

        return resourcePackagerTask
    }

    private void addResourceFromSources(Jar jar, SourceSet sourceSet) {
        if (!extension.resources.enableResourcesFromSourceDirs) {
            return
        }
        sourceSet.java.srcDirs.each { src ->
            project.logger.debug("Adding path $src to $jar.name")
            jar.from(src) {
                extension.resources.resourcesFromSourceDirExcludes.each { filter ->
                    exclude(filter)
                }
            }
        }
    }

    public Task configureXcodeInternalTask(JavaPluginConvention javaConvention) {
        // Get platform
        def targetVariant = TargetVariant.getTargetVariantByPlatformName(TaskUtil.getRequiredEnvVar("PLATFORM_NAME"))

        // Get configuration
        def modeVariant = ModeVariant.getModeVariant(TaskUtil.getRequiredEnvVar("CONFIGURATION"))

        // Get architectures
        def architectures = TaskUtil.getRequiredEnvVar("ARCHS")

        // Get source set name
        def sourceSetValue = TaskUtil.getRequiredEnvVar("MOE_BUILD_SOURCE_SET", SourceSet.MAIN_SOURCE_SET_NAME)
        def sourceSet = TaskUtil.getSourceSet(javaConvention, sourceSetValue)

        // Collect architectures
        def architectureVariants = []
        for (String archName in architectures.split(" ")) {
            ArchitectureVariant var = ArchitectureVariant.getArchitectureVariantByName(archName)
            architectureVariants.add(var)
        }

        // Create task

        final String taskName = createTaskName(XCODE_INTERNAL_TASK_NAME)
        Task internalTask = project.tasks.create(taskName, Task.class)

        // Collect dependency tasks
        for (ArchitectureVariant arch in architectureVariants) {
            final String providerTaskName = createTaskName(sourceSet.name, modeVariant.name, arch.archName,
                    targetVariant.getPlatformName(), XcodeProvider.NAME)
            XcodeProvider packagerTask = (XcodeProvider) project.tasks.getByName(providerTaskName)
            internalTask.dependsOn packagerTask
        }

        return internalTask
    }

    public static String createTaskName(String... components) {
        String[] cmp2 = new String[components.length + 1]
        System.arraycopy(components, 0, cmp2, 1, components.length)
        cmp2[0] = MOE
        StringUtil.camelcase(cmp2)
    }
}
