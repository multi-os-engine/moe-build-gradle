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
import org.moe.gradle.internal.AnnotationChecker
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

import java.util.jar.JarFile

class StartupProvider extends BaseTask {

    static String NAME = "StartupProvider"

    /*
    Task inputs
     */

    @InputFiles
    Collection<File> inputFiles

    /*
    Task outputs
     */

    @OutputFile
    File preregisterFile

    @OutputFile
    File log

    /*
    Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|> preregisterFile: ${getPreregisterFile()}")
        project.logger.debug("|> log: ${getLog()}")

        getLog().text = ""
        getPreregisterFile().text = ""

        getInputFiles().each {
            getLog().text += "Checking: ${it.absolutePath}\n"
            JarFile file = new JarFile(it)
            file.entries().each {
                if (!it.getName().endsWith(".class")) {
                    return
                }

                AnnotationChecker checker = AnnotationChecker.getRegisterOnStartupChecker(file.getInputStream(it))
                if (checker.hasAnnotation()) {
                    getLog().text += "Found: ${checker.getName()}\n"
                    getPreregisterFile().text += "${checker.getName()}\n"
                }
            }
        }
    }

    public static String getTaskName(SourceSet sourceSet) {
        return BaseTask.composeTaskName(sourceSet.name, StartupProvider.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = StartupProvider.NAME
        final String ELEMENTS_DESC = '<SourceSet>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        return project.tasks.addRule("Pattern: $PATTERN: Creates the preregister.txt file."
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
            StartupProvider.create(project, sourceSet)
        })
    }

    public static StartupProvider create(Project project, SourceSet sourceSet) {
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.name}"

        // Create task
        final String taskName = getTaskName(sourceSet)
        StartupProvider startupProviderTask = project.tasks.create(taskName, StartupProvider.class)
        startupProviderTask.description = "Generates preregister.txt file ($outPath)."

        // Add dependencies
        final String proguardTaskName = ProGuard.getTaskName(sourceSet)
        ProGuard proguardTask = (ProGuard) project.tasks.getByName(proguardTaskName)
        startupProviderTask.dependsOn proguardTask

        // Update convention mapping
        startupProviderTask.conventionMapping.inputFiles = {
            [proguardTask.getOutJar()]
        }
        startupProviderTask.conventionMapping.preregisterFile = {
            project.file("${project.buildDir}/${outPath}/preregister.txt")
        }
        startupProviderTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/StartupProvider.log")
        }

        return startupProviderTask
    }
}
