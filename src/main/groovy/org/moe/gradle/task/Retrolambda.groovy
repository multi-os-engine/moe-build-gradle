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

import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.apache.commons.lang.StringUtils
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

class Retrolambda extends BaseTask {

    static String NAME = "Retrolambda"

    /*
    Task inputs
     */

    @InputFiles
    Collection<File> inputFiles

    @InputFiles
    Collection<File> classpathFiles

    @Input
    boolean defaultMethods = true

    @Input
    boolean natjSupport = true

    @InputFile
    File retroExec

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

    File inputDir

    File outputDir

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|< classpathFiles: ${getClasspathFiles()}")
        project.logger.debug("|< defaultMethods: ${getDefaultMethods()}")
        project.logger.debug("|< natjSupport: ${getNatjSupport()}")
        project.logger.debug("|< retroExec: ${getRetroExec()}")
        project.logger.debug("|> outputFile: ${getOutputFile()}")
        project.logger.debug("|> log: ${getLog()}")

        securedLoggableAction(getLog()) {
            doRetro()
        }
    }

    def doRetro() {
        project.copy {
            getInputFiles().each { from project.zipTree(it.absolutePath) }
            include "**/*.class"
            into getInputDir()
        }
        project.javaexec {
            args "-Dretrolambda.inputDir=${getInputDir().absolutePath}"
            args "-Dretrolambda.classpath=${StringUtils.join(getClasspathFiles().collect { it.absolutePath }, System.getProperty("path.separator"))}"
            args "-Dretrolambda.defaultMethods=${getDefaultMethods()}"
            args "-Dretrolambda.natjSupport=${getNatjSupport()}"
            args "-Dretrolambda.outputDir=${getOutputDir().absolutePath}"
            main "-jar"
            args "${getRetroExec().absolutePath}"

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    public static String getTaskName(SourceSet sourceSet) {
        return BaseTask.composeTaskName(sourceSet.name, Retrolambda.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = Retrolambda.NAME
        final String ELEMENTS_DESC = '<SourceSet>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates a Retrolambda-d jar."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

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
            Retrolambda.create(project, sourceSet)
        })
    }

    public static Retrolambda create(Project project, SourceSet sourceSet) {
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.getName()}/retro"

        // Create task
        final String taskName = getTaskName(sourceSet)
        Retrolambda retrolambdaTask = project.tasks.create(taskName, Retrolambda.class)
        retrolambdaTask.description = "Generates retrolambda files ($outPath)."

        // Add dependencies
        final String proguardTaskName = ProGuard.getTaskName(sourceSet)
        ProGuard proguardTask = (ProGuard) project.tasks.getByName(proguardTaskName)
        retrolambdaTask.dependsOn proguardTask

        // Update convention mapping
        retrolambdaTask.conventionMapping.inputFiles = {
            [proguardTask.getOutJar()]
        }
        retrolambdaTask.conventionMapping.inputDir = {
            project.file("${project.buildDir}/${outPath}/classes/")
        }
        retrolambdaTask.conventionMapping.classpathFiles = {
            def list = [project.file("${project.buildDir}/${outPath}/classes/")]
            list.addAll(proguardTask.getLibraryJars())
            list
        }
        retrolambdaTask.conventionMapping.retroExec = {
            project.moe.sdk.getRetrolambdaJar()
        }
        retrolambdaTask.conventionMapping.outputDir = {
            project.file("${project.buildDir}/${outPath}/retro/")
        }
        retrolambdaTask.conventionMapping.outputFile = {
            project.file("${project.buildDir}/${outPath}/retro.jar")
        }
        retrolambdaTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/retro.log")
        }
        retrolambdaTask.conventionMapping.natjSupport = {
            !project.hasProperty("moe.sdk.skip_ios")
        }
        return retrolambdaTask
    }
}
