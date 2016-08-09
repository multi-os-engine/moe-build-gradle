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

import org.moe.common.defaults.Dex2OatDefaults
import org.moe.common.variant.ModeVariant
import org.moe.gradle.BasePlugin
import org.moe.gradle.option.Dex2OatOptions
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

class Dex2Oat extends BaseTask {

    static String NAME = "Dex2Oat"

    /*
	Task inputs
     */

    @Input
    String archFamily

    @Input
    long base

    @InputFile
    File imageClasses

    @Input
    boolean isDebug

    @InputFiles
    Collection<File> inputFiles

    @InputFile
    File dex2oatExec

    @Nested
    Dex2OatOptions dex2oatOptions

    /*
    Task outputs
     */

    @OutputFile
    File destImage

    @OutputFile
    File destOat

    @OutputFile
    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< archFamily: ${getArchFamily()}")
        project.logger.debug("|< base: ${getBase()}")
        project.logger.debug("|< imageClasses: ${getImageClasses()}")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|< dex2oatExec: ${getDex2oatExec()}")
        project.logger.debug("|< dex2oatOptions: ${getDex2oatOptions()}")
        project.logger.debug("|> destImage: ${getDestImage()}")
        project.logger.debug("|> destOat: ${getDestOat()}")
        project.logger.debug("|> log: ${getLog()}")

        securedLoggableAction(getLog()) {
            doDex2Oat()
        }
    }

    def doDex2Oat() {
        project.exec {
            // Set executable
            executable = getDex2oatExec()

            // Set target options
            args "--instruction-set=${getArchFamily()}"
            args "--base=0x${Long.toHexString(getBase())}"

            // Set compiler backend
            args "--compiler-backend=${getDex2oatOptions().getCompilerBackend()}"

            // Include or not include ELF symbols in oat file
            if (isDebug) {
                args "--generate-debug-info"
            } else {
                args "--no-generate-debug-info"
            }

            // Set files
            args "--image=${getDestImage().absolutePath}"
            args "--image-classes=${getImageClasses().absolutePath}"
            args "--oat-file=${getDestOat().absolutePath}"

            // Set inputs
            StringBuilder dexFiles = new StringBuilder();
            getInputFiles().each {
                if (dexFiles.length() > 0) {
                    dexFiles.append(':')
                }
                dexFiles.append(it.absolutePath)
            }
            args "--dex-file=${dexFiles}"

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    public static String getTaskName(SourceSet sourceSet, ModeVariant modeVariant, String archFamily) {
        return BaseTask.composeTaskName(sourceSet.name, modeVariant.name, archFamily, Dex2Oat.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = Dex2Oat.NAME
        final String ELEMENTS_DESC = '<SourceSet><Mode><ArchitectureFamily>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates art and oat files."
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
            String archFamily = Dex2OatDefaults.getArchitectureFamilyByName(elements[2])
            Dex2Oat.create(project, sourceSet, modeVariant, archFamily)
        })
    }

    public static Dex2Oat create(Project project, SourceSet sourceSet, ModeVariant modeVariant, String archFamily) {
        // Helpers
        final def sdk = project.moe.sdk

        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.name}/${modeVariant.name}/$archFamily"

        // Create task
        final String taskName = getTaskName(sourceSet, modeVariant, archFamily)
        Dex2Oat dex2oatTask = project.tasks.create(taskName, Dex2Oat.class)
        dex2oatTask.description = "Generates art+oat files ($outPath)."
        dex2oatTask.dex2oatOptions = project.moe.dex2oatOptions
        dex2oatTask.archFamily = archFamily

        // Add dependencies
        final String dexTaskName = Dex.getTaskName(sourceSet, modeVariant)
        Dex dexTask = (Dex) project.tasks.getByName(dexTaskName)
        dex2oatTask.dependsOn dexTask

        // Update convention mapping
        dex2oatTask.conventionMapping.base = {
            Dex2OatDefaults.getDefaultBaseForArchFamily(dex2oatTask.getArchFamily())
        }
        dex2oatTask.conventionMapping.imageClasses = {
            sdk.getPreloadedClasses()
        }
        dex2oatTask.conventionMapping.inputFiles = {
            def files = [
                    dexTask.outputs.files.find {
                        it.absolutePath.endsWith('.jar')
                    }
            ]
            if (!sdk.getFullTrim()) {
                files.addAll(sdk.getMainDexFiles().getFiles())
                if (project.hasProperty("moe.sdk.trim_ios") && sdk.getMainJars().getFiles().contains(sdk.getIOSJar())) {
                    files.remove(sdk.getIOSDex())
                }
            }
            files
        }
        dex2oatTask.conventionMapping.dex2oatExec = {
            sdk.getDex2OatExec()
        }
        dex2oatTask.conventionMapping.destImage = {
            project.file("${project.buildDir}/${outPath}/image.art")
        }
        dex2oatTask.conventionMapping.destOat = {
            project.file("${project.buildDir}/${outPath}/application.oat")
        }
        dex2oatTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/dex2oat.log")
        }
        dex2oatTask.conventionMapping.isDebug = {
            modeVariant.isDebug()
        }

        return dex2oatTask
    }
}
