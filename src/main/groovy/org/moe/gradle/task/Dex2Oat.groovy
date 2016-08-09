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

    static final String NAME = "Dex2Oat"
    static final String NATIVE_LIB_NAME = "natives.a"
    static final String DEX2OAT_LOG_NAME = "dex2oat.log"
    static final String AR_LOG_NAME = "ar.log"
    static final String NATIVE_INTERMEDIATES_NAME = "intermediates"


    /*
	Task inputs
     */

    @Input
    String archFamily

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
    File destNativeLib

    @OutputFile
    File dex2oatLog

    @OutputFile
    File arLog

    /*
    Task intermediates
    */

    @OutputDirectory
    File nativeLibIntermediates;

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< archFamily: ${getArchFamily()}")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|< dex2oatExec: ${getDex2oatExec()}")
        project.logger.debug("|< dex2oatOptions: ${getDex2oatOptions()}")
        project.logger.debug("|> destNativeLib: ${getDestNativeLib()}")
        project.logger.debug("|> dex2oatLog: ${getDex2oatLog()}")
        project.logger.debug("|> arLog: ${getArLog()}")

        securedLoggableAction(getDex2oatLog()) {
            doDex2Oat()
        }

        securedLoggableAction(getArLog()) {
            doAr()
        }
    }

    def doDex2Oat() {
        project.exec {
            // Set executable
            executable = getDex2oatExec()

            // Set target options
            args "--instruction-set=${getArchFamily()}"

            // Set compiler backend
            args "--compiler-backend=${getDex2oatOptions().getCompilerBackend()}"

            // Include or not include ELF symbols in oat file
            if (isDebug) {
                args "--generate-debug-info"
            } else {
                args "--no-generate-debug-info"
            }

            // Set files
            args "--native-output=${getNativeLibIntermediates().absolutePath}"

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
            FileOutputStream ostream = new FileOutputStream(getDex2oatLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    def doAr() {
        // Link the object files together
        project.exec {
            // Set executable
            executable = "ar"

            // Set commands
            args "-rcs"

            // Set target library
            args "${getDestNativeLib().absolutePath}"

            // Set source object files
            project.fileTree(dir: getNativeLibIntermediates(), include: '*.o').each {
                args "${it.absolutePath}"
            }

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getArLog());
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
        project.tasks.addRule("Pattern: $PATTERN: Creates native static library file."
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
        dex2oatTask.description = "Generates native static library file ($outPath/${NATIVE_LIB_NAME})."
        dex2oatTask.dex2oatOptions = project.moe.dex2oatOptions
        dex2oatTask.archFamily = archFamily

        // Add dependencies
        final String dexTaskName = Dex.getTaskName(sourceSet, modeVariant)
        Dex dexTask = (Dex) project.tasks.getByName(dexTaskName)
        dex2oatTask.dependsOn dexTask

        // Set intermediate directory
        dex2oatTask.nativeLibIntermediates = project.file("${project.buildDir}/${outPath}/${NATIVE_INTERMEDIATES_NAME}")

        // Update convention mapping
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
        dex2oatTask.conventionMapping.destNativeLib = {
            project.file("${project.buildDir}/${outPath}/${NATIVE_LIB_NAME}")
        }
        dex2oatTask.conventionMapping.dex2oatLog = {
            project.file("${project.buildDir}/${outPath}/${DEX2OAT_LOG_NAME}")
        }
        dex2oatTask.conventionMapping.arLog = {
            project.file("${project.buildDir}/${outPath}/${AR_LOG_NAME}")
        }
        dex2oatTask.conventionMapping.isDebug = {
            modeVariant.isDebug()
        }

        return dex2oatTask
    }
}
