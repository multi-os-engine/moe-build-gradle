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

import org.moe.common.developer.ProvisioningProfile
import org.moe.common.defaults.Dex2OatDefaults
import org.moe.common.variant.ModeVariant
import org.moe.common.variant.TargetVariant
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.moe.gradle.util.XcodeUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

class XcodeBuild extends BaseTask {

    static String NAME = "XcodeBuild"

    /*
	Task inputs
     */

    /**
     * This field is used to disable incremental building on this task,
     * Xcode will do it itself.
     */

    @InputFiles
    List<File> oatFiles

    @InputFiles
    List<File> imageFiles

    @InputFiles
    Set<File> appResources

    @Input
    String target

    @Input
    String configuration

    @Input
    String sdk

    @InputDirectory
    File xcodeProjectFile

    @Input
    List<String> additionalParameters = []

    @Optional
    @Input
    String provisioningProfile

    @Optional
    @Input
    String signingIdentity

    @InputFile
    File xcodebuildExec

    /*
    Task outputs
     */

    @OutputDirectory
    File dstRoot

    @OutputDirectory
    File objRoot

    @OutputDirectory
    File symRoot

    @OutputDirectory
    File sharedPrecompsDir

    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< appResources: ${getAppResources()}")
        project.logger.debug("|< target: ${getTarget()}")
        project.logger.debug("|< configuration: ${getConfiguration()}")
        project.logger.debug("|< imageFiles: ${getImageFiles()}")
        project.logger.debug("|< sdk: ${getSdk()}")
        project.logger.debug("|< dstRoot: ${getDstRoot()}")
        project.logger.debug("|< oatFiles: ${getOatFiles()}")
        project.logger.debug("|< objRoot: ${getObjRoot()}")
        project.logger.debug("|< symRoot: ${getSymRoot().getAbsolutePath()}")
        project.logger.debug("|< sharedPrecompsDir: ${getSharedPrecompsDir()}")
        project.logger.debug("|< additionalParameters: ${getAdditionalParameters()}")
        project.logger.debug("|< provisioningProfile: ${getProvisioningProfile()}")
        project.logger.debug("|< signingIdentity: ${getSigningIdentity()}")
        project.logger.debug("|< xcodebuildExec: ${getXcodebuildExec()}")
        project.logger.debug("|> log: ${getLog()}")

        securedLoggableAction(getLog()) {
            xcodebuild()
        }
    }

    def xcodebuild() {
        project.exec {
            // Set executable
            executable = getXcodebuildExec()

            // Set options
            args "-target"
            args getTarget()
            args "-configuration"
            args getConfiguration()
            args "-sdk"
            args getSdk()
            args "-project"
            args getXcodeProjectFile().getAbsolutePath()

            getAdditionalParameters().each {
                args it
            }

            args "DSTROOT=${getDstRoot().absolutePath}"
            args "OBJROOT=${getObjRoot().absolutePath}"
            args "SYMROOT=${getSymRoot().absolutePath}"
            args "SHARED_PRECOMPS_DIR=${getSharedPrecompsDir().absolutePath}"

            final String provisioningProfile = getProvisioningProfile()
            if (provisioningProfile != null && !provisioningProfile.isEmpty()) {
                String uuid
                File fileProvisioningProfile = new File(provisioningProfile)
                if (fileProvisioningProfile.exists()) {
                    try {
                        uuid = ProvisioningProfile.getUUIDFromProfile(fileProvisioningProfile)
                    } catch (Exception e) {
                        throw new GradleException(e.getMessage(), e)
                    }
                    args "PROVISIONING_PROFILE=${uuid}"
                } else {
                    throw new GradleException("Failed to find provisioning profile: " + provisioningProfile)
                }
            } else {
                project.logger.warn("Provisioning profile is not specified! Default one will be chosen!")
            }

            final String signingIdentity = getSigningIdentity()
            if (signingIdentity != null && !signingIdentity.isEmpty()) {
                args "CODE_SIGN_IDENTITY=${signingIdentity}"
            } else {
                project.logger.warn("Signing identity is not specified! Default one will be chosen!")
            }

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    public static String getTaskName(SourceSet sourceSet, ModeVariant modeVariant, TargetVariant targetVariant) {
        return BaseTask.composeTaskName(sourceSet.name, modeVariant.name, targetVariant.platformName, XcodeBuild.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = XcodeBuild.NAME
        final String ELEMENTS_DESC = '<SourceSet><Mode><Platform>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates .app files."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 3, ELEMENTS_DESC)

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
            ModeVariant modeVariant = ModeVariant.getModeVariant(elements[1])
            TargetVariant targetVariant = TargetVariant.getTargetVariantByPlatformName(elements[2])
            XcodeBuild.create(project, sourceSet, modeVariant, targetVariant)
        })
    }

    public
    static XcodeBuild create(Project project, SourceSet sourceSet, ModeVariant modeVariant, TargetVariant targetVariant) {
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/xcodebuild"

        // Create task
        final String taskName = getTaskName(sourceSet, modeVariant, targetVariant)
        XcodeBuild xcodebuildTask = project.tasks.create(taskName, XcodeBuild.class)
        xcodebuildTask.description = "Builds .app files ($outPath)."

        List<File> oatFiles = new ArrayList<File>()
        List<File> imageFiles = new ArrayList<File>()

        // Add dependencies
        def supported = targetVariant.getArchitectureVariants().findAll { architectureVariant ->
            String family = architectureVariant.familyName
        }
        supported.each { architectureVariant ->
            String xcodeProviderTaskName = XcodeProvider.getTaskName(sourceSet, modeVariant, architectureVariant,
                    targetVariant)
            XcodeProvider xcodeProvider = project.tasks.getByName(xcodeProviderTaskName)

            xcodebuildTask.dependsOn xcodeProvider
        }

        String projectGeneratorName = XcodeProjectGenerator.getTaskName()
        XcodeProjectGenerator projectGenerator = project.tasks.getByName(projectGeneratorName)
        xcodebuildTask.dependsOn projectGenerator

        String targetName
        if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            targetName = projectGenerator.getProjectName()
        } else {
            String testTarget = project.moe.xcode.testTarget
            if ((testTarget == null) || testTarget.isEmpty()) {
                testTarget = projectGenerator.getProjectName() + "-Test"
            }
            targetName = testTarget
        }

        // Update convention mapping
        xcodebuildTask.conventionMapping.target = {
            targetName
        }
        xcodebuildTask.conventionMapping.configuration = {
            modeVariant.name
        }
        xcodebuildTask.conventionMapping.sdk = {
            targetVariant.platformName
        }

        xcodebuildTask.conventionMapping.oatFiles = {
            oatFiles
        }
        xcodebuildTask.conventionMapping.imageFiles = {
            imageFiles
        }

        xcodebuildTask.conventionMapping.appResources = {
            sourceSet.getResources().files
        }

        File xcodeProjectFile = new File(projectGenerator.getXcodeProjectDir(), projectGenerator.getProjectName() + ".xcodeproj")
        xcodebuildTask.conventionMapping.xcodeProjectFile = {
            xcodeProjectFile
        }

        xcodebuildTask.conventionMapping.dstRoot = {
            project.file("${project.buildDir}/${outPath}/dst")
        }
        xcodebuildTask.conventionMapping.objRoot = {
            project.file("${project.buildDir}/${outPath}/obj")
        }
        xcodebuildTask.conventionMapping.symRoot = {
            new File(projectGenerator.getSymRoot())
        }
        xcodebuildTask.conventionMapping.sharedPrecompsDir = {
            project.file("${project.buildDir}/${outPath}/shared_precomps")
        }
        xcodebuildTask.conventionMapping.additionalParameters = {
            ["MOE_GRADLE_EXTERNAL_BUILD=YES", "ONLY_ACTIVE_ARCH=NO"]
        }
        xcodebuildTask.conventionMapping.xcodebuildExec = {
            XcodeUtil.get().getXcodeBuildPath()
        }
        xcodebuildTask.conventionMapping.provisioningProfile = {
            project.moe.ipaOptions.provisioningProfile
        }
        xcodebuildTask.conventionMapping.signingIdentity = {
            project.moe.ipaOptions.signingIdentity
        }

        xcodebuildTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/xcodebuild-${new Date().format("yyyy.MM.dd-hh.mm.ss")}.log")
        }

        return xcodebuildTask
    }

}
