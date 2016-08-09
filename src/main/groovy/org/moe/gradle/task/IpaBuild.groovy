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

import org.moe.common.variant.ModeVariant
import org.moe.common.variant.TargetVariant
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

class IpaBuild extends BaseTask {
    static String NAME = "IpaBuild"

    /*
	Task inputs
     */

    @InputDirectory
    File inputApp

    @Optional
    @Input
    String provisioningProfile

    @Optional
    @Input
    String signingIdentity

    /*
    Task outputs
     */

    @OutputFile
    File outputIpa

    @OutputFile
    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< inputApp: ${getInputApp()}")
        project.logger.debug("|< outputIpa: ${getOutputIpa()}")
        project.logger.debug("|< provisioningProfile: ${getProvisioningProfile()}")
        project.logger.debug("|< signingIdentity: ${getSigningIdentity()}")
        project.logger.debug("|> log: ${getLog()}")

        securedLoggableAction(getLog()) {
            ipaBuild()
        }
    }

    def ipaBuild() {
        project.exec {
            // Set executable
            executable = "xcrun"

            // Set options
            args "-sdk"
            args "iphoneos"
            args "PackageApplication"
            args "-v"
            args getInputApp().absolutePath
            args "-o"
            args getOutputIpa().absolutePath

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    public static String getTaskName() {
        return composeTaskName(NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {

        // Prepare constants
        final String TASK_NAME = NAME
        final String PATTERN = "${BasePlugin.MOE}${TASK_NAME}"

        // Add rule
        return project.tasks.addRule("Pattern: $PATTERN: Creates .ipa files."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, SourceSet.MAIN_SOURCE_SET_NAME)
            ModeVariant modeVariant = ModeVariant.getModeVariant(ModeVariant.RELEASE_NAME)
            TargetVariant targetVariant = TargetVariant.getTargetVariantByPlatformName(TargetVariant.IOS_DEVICE_PLATFORM_NAME)
            create(project, sourceSet, modeVariant, targetVariant)
        })
    }

    public
    static IpaBuild create(Project project, SourceSet sourceSet, ModeVariant modeVariant, TargetVariant targetVariant) {

        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/xcodebuild"

        // Create task
        final String taskName = getTaskName()
        IpaBuild ipaBuild = project.tasks.create(taskName, IpaBuild.class)
        ipaBuild.description = "Builds .ipa file ($outPath)."

        // Add dependencies
        final String xcodeBuildTaskName = XcodeBuild.getTaskName(sourceSet, modeVariant, targetVariant)
        XcodeBuild xcodeBuildTask = (XcodeBuild) project.tasks.getByName(xcodeBuildTaskName)
        ipaBuild.dependsOn xcodeBuildTask

        final String buildPath = "${xcodeBuildTask.getSymRoot().absolutePath}/${modeVariant.name}-${targetVariant.platformName}"
        final String targetName = project.moe.xcode.mainTarget

        // Update convention mapping
        ipaBuild.conventionMapping.inputApp = {
            project.file("${buildPath}/${targetName}.app")
        }
        ipaBuild.conventionMapping.outputIpa = {
            project.file("${buildPath}/${targetName}.ipa")
        }
        ipaBuild.conventionMapping.provisioningProfile = {
            project.moe.ipaOptions.provisioningProfile
        }
        ipaBuild.conventionMapping.signingIdentity = {
            project.moe.ipaOptions.signingIdentity
        }
        ipaBuild.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/ipa-${new Date().format("yyyy.MM.dd-hh.mm.ss")}.log")
        }

        return ipaBuild
    }
}
