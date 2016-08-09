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

import java.nio.file.Files;
import java.nio.file.Path;

import org.moe.common.utils.OsUtils
import org.moe.common.variant.ModeVariant
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*

class Dex extends BaseTask {

    static String NAME = "Dex"

    /*
    Task inputs
     */

    @Input
    boolean debug

    @InputFiles
    Collection<File> inputFiles

    @InputFiles
    Collection<File> libraries

    @InputFile
    File dxExec

    @Input
    List<String> extraArgs = []

    /*
    Task outputs
     */

    @OutputFile
    File destJar

    @OutputFile
    File log

    /*
    Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< debug: $debug")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|< libraries: ${getLibraries()}")
        project.logger.debug("|< dxExec: ${getDxExec()}")
        project.logger.debug("|> destJar: ${getDestJar()}")
        project.logger.debug("|> log: ${getLog()}")

        securedLoggableAction(getLog()) {
            doDx()
        }
    }

    def doDx() {

        if (OsUtils.windows) {
//            project.javaexec {
//                args '-DJXmx4096m'
//
//                main "-jar"
//                args "${getDxExec().absolutePath}"
//                prepareArgumentsList().each { args it }
//
//                // Fail build if dex fails
//                setIgnoreExitValue false
//
//                // Set logging
//                FileOutputStream ostream = new FileOutputStream(getLog());
//                setErrorOutput(ostream)
//                setStandardOutput(ostream)
//            }
        } else {
            project.exec {
                // Set executable
                executable = getDxExec()

                prepareArgumentsList().each { args it }

                // Fail build if dex fails
                setIgnoreExitValue false

                // Set logging
                FileOutputStream ostream = new FileOutputStream(getLog());
                setErrorOutput(ostream)
                setStandardOutput(ostream)
            }
        }
    }

    public def prepareArgumentsList() {
        Collection<String> args = []

        // Set output
        args.add("${getDestJar().absolutePath}")

        // Set options
        if (debug) {
            args.add('-g')
        }

        args.add('--verbose')
        args.add('WARNING')
        args.add('--multi-dex')
        args.add('NATIVE')
        args.add('-D')
        args.add('jack.android.min-api-level=24')

        // Set extra arguments
        for (String extra : getExtraArgs()) {
            args.add(extra)
        }

        // Set inputs
        getInputFiles().each {
            args.add('--import')
            args.add(jarForPath(it.absolutePath))
        }
        getLibraries().each {
            args.add('--import')
            args.add(jarForPath(it.absolutePath))
        }
        args
    }

    // TODO: A temporal workaround for jack not beeng able to handle input directories containing
    // Java class files. We create jars from those input directories and pass them to jack instead.
    public String jarForPath(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            File temp = Files.createTempDirectory(null).toFile();
            temp.deleteOnExit();
            file = new File(temp, "input.jar")

            project.exec {
                // Set executable
                commandLine 'bash', '-c', "cd ${path}; zip -r ${file.absolutePath} ."

                // Fail build if dex fails
                setIgnoreExitValue false

                // Set logging
                FileOutputStream ostream = new FileOutputStream(getLog());
                setErrorOutput(ostream)
                setStandardOutput(ostream)
            }
        }
        return file.absolutePath;
    }

    public static String getTaskName(SourceSet sourceSet, ModeVariant modeVariant) {
        return BaseTask.composeTaskName(sourceSet.name, modeVariant.name, Dex.NAME)
    }

    public static Rule addRue(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = Dex.NAME
        final String ELEMENTS_DESC = '<SourceSet><Mode>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates a Dexed jar."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 2, ELEMENTS_DESC)

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
            ModeVariant modeVariant = ModeVariant.getModeVariant(elements[1])
            Dex.create(project, sourceSet, modeVariant)
        })
    }

    public static Dex create(Project project, SourceSet sourceSet, ModeVariant modeVariant) {
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.getName()}/${modeVariant.getName()}"

        // Create task
        final String taskName = getTaskName(sourceSet, modeVariant)
        Dex dexTask = project.tasks.create(taskName, Dex.class)
        dexTask.description = "Generates dex files ($outPath)."
        dexTask.debug = modeVariant.isDebug()

        // Add dependencies
        final String retroTaskName = Retrolambda.getTaskName(sourceSet)
        Retrolambda retroTask = (Retrolambda) project.tasks.getByName(retroTaskName)
        dexTask.dependsOn retroTask

        // Update convention mapping
        dexTask.conventionMapping.inputFiles = {
            [retroTask.getOutputDir()]
        }
        dexTask.conventionMapping.libraries = {
            Collections.emptyList()
        }
        dexTask.conventionMapping.dxExec = {
            project.moe.sdk.getDxExec()
        }
        dexTask.conventionMapping.destJar = {
            project.file("${project.buildDir}/${outPath}/classes.jar")
        }
        dexTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/dx.log")
        }

        return dexTask
    }
}
