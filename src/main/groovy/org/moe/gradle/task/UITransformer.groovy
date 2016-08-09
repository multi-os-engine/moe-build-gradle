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

import org.moe.common.utils.OsUtils
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*


class UITransformer extends BaseTask {
    static String NAME = "UITransformer"

    File uiResourcesDir

    /*
	Task inputs
     */

    @Optional
    @InputFile
    File uiTransformerJar

    @Optional
    @InputDirectory
    File uiTransformerRes

    @Optional
    @InputDirectory
    File layoutDir

    /*
    Task outputs
     */

    @OutputFile
    File outputFile

    @OutputFile
    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        if (getLayoutDir() == null || !getLayoutDir().exists()) {
            project.logger.warn("UITransformer build was skipped.")
            return
        }
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< log: ${getLog()}")
        project.logger.debug("|< uiTransformerJar: ${getUiTransformerJar()}")
        project.logger.debug("|< uiTransformerRes: ${getUiTransformerRes()}")
        project.logger.debug("|< outputFile: ${getOutputFile()}")
        project.logger.debug("|< uiResourcesDir: ${getUiResourcesDir()}")

        securedLoggableAction(getLog()) {
            runUITransformer()
        }
        securedLoggableAction(getLog()) {
            runIbtool()
        }
    }

    def runUITransformer() {
        project.javaexec {

            main "-cp"
            args "${getUiTransformerJar().absolutePath}"
            args "org.moe.transformer.OSXUITransformer"
            args "--ixml-res-path=${getUiResourcesDir().absolutePath}"
            args "--out-format=storyboard"
            args "--out-filename=MainUI"
            args "--out-path=${getUiResourcesDir().absolutePath}"
            args "--uitransformer-res-path=${getUiTransformerRes().absolutePath}"

            // Fail build if uiTransformer fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
        if (getOutputFile() == null || !getOutputFile().exists()) {
            throw new GradleException("Failed to transform .ixml to .storyboard ")
        }
    }

    def runIbtool() {
        if (!OsUtils.isMac()) {
            return
        }
        
        project.exec {
            executable = 'ibtool'

            args getOutputFile().absolutePath
            args "--write"
            args getOutputFile().absolutePath
            args "--update-frames"
            args "--errors"
            args "--warnings"
            args "--notices"

            // FailxitValue fa build if ibtool fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }

        if (getOutputFile() == null || !getOutputFile().exists()) {
            throw new GradleException("Storyboard can't be found after calling ibtool: " + getOutputFile().absolutePath)
        }
    }

    public static String getTaskName(SourceSet sourceSet) {
        return BaseTask.composeTaskName(sourceSet.name, UITransformer.NAME)
    }

    public static void addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = UITransformer.NAME
        final String ELEMENTS_DESC = '<SourceSet>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        final def sdk = project.moe.sdk
        File uiTransformerJar = sdk.getUITransformerJar()
        File uiTransformerRes = sdk.getUITransformerRes()
        File layoutDir = project.file("${sdk.getUiResourcesDir().getAbsolutePath()}${File.separator}layout${File.separator}")

        if (!uiTransformerJar.exists() || !uiTransformerRes.exists() || !layoutDir.exists()) {
            return
        }

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: run uiTransformer."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            String customStoryboardPath = project.moe.xcode.mainUIStoryboardPath
            if (customStoryboardPath != null && !customStoryboardPath.isEmpty()) {
                return null;
            }

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 1, ELEMENTS_DESC)

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
            UITransformer.create(project, sourceSet)
        })
    }

    public static UITransformer create(Project project, SourceSet sourceSet) {
        // Helpers
        final def sdk = project.moe.sdk
        String separator = File.separator
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}$separator${sourceSet.name}"

        // Create task
        final String taskName = getTaskName(sourceSet)
        UITransformer uiTransformerTask = project.tasks.create(taskName, UITransformer.class)
        uiTransformerTask.description = "Run uiTransformer ($outPath)."

        File layoutDir = project.file("${sdk.getUiResourcesDir().getAbsolutePath()}${separator}layout${separator}")
//        if (!layoutDir.exists()) {
//            project.logger.debug("Folder 'layout' was not found.")
//            layoutDir = null
//        }

        uiTransformerTask.conventionMapping.uiTransformerJar = {
            sdk.getUITransformerJar()
        }

        uiTransformerTask.conventionMapping.uiTransformerRes = {
            sdk.getUITransformerRes()
        }

        uiTransformerTask.conventionMapping.layoutDir = {
            layoutDir
        }

        uiTransformerTask.conventionMapping.uiResourcesDir = {
            sdk.getUiResourcesDir()
        }

        uiTransformerTask.conventionMapping.outputFile = {
            project.file("${sdk.getUiResourcesDir().getAbsolutePath()}${separator}MainUI.storyboard")
        }

        final String logPath = "${project.buildDir}${separator}${outPath}${separator}uiTransformer.log"
        uiTransformerTask.conventionMapping.log = {
            project.file(logPath)
        }

        return uiTransformerTask
    }
}
