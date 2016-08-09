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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile

class ProGuard extends BaseTask {

    static String NAME = "ProGuard"

    /*
	Task inputs
     */

    @InputFile
    File configBaseFile

    @InputFiles
    Collection<File> inJars

    @InputFile
    File proGuardJar

    @Optional
    @InputFiles
    Collection<File> libraryJars

    @Optional
    @InputFile
    File projectLevelConfigBaseFile

    @Optional
    @InputFile
    File configAppendFile

    /*
    Task outputs
     */

    @OutputFile
    File outJar

    @OutputFile
    File configFile

    @OutputFile
    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|> configBaseFile: ${getConfigBaseFile()}")
        project.logger.debug("|> inJars: ${getInJars()}")
        project.logger.debug("|> libraryJars: ${getLibraryJars()}")
        project.logger.debug("|> proGuardJar: ${getProGuardJar()}")
        project.logger.debug("|> projectLevelConfigBaseFile: ${getProjectLevelConfigBaseFile()}")
        project.logger.debug("|> configAppendFile: ${getConfigAppendFile()}")
        project.logger.debug("|< outJar: ${getOutJar()}")
        project.logger.debug("|< configFile: ${getConfigFile()}")
        project.logger.debug("|< log: ${getLog()}")

        securedLoggableAction(getLog()) {
            doConfigAction()
            doTaskAction()
        }
    }

    def doConfigAction() {
        def conf = ''
        conf += getSegmentComment("Generating -injars")
        getInJars().each {
            if (it.exists()) {
                conf += "-injars $it.absolutePath(!**.framework/**,!**.bundle/**)\n"
            } else {
                project.logger.warn("InJar $it for ProGuard task doesn't exist!")
            }
        }
        conf += getSegmentComment("Generating -outjars")
        conf += "-outjars \"${getOutJar()}\"\n"
        if (getLibraryJars() != null) {
            conf += getSegmentComment("Generating -libraryjars")
            getLibraryJars().each {
                if (it.exists()) {
                    conf += "-libraryjars $it.absolutePath\n"
                } else {
                    project.logger.warn("LibraryJar $it for ProGuard task doesn't exist!")
                }
            }
        }
        if (getProjectLevelConfigBaseFile() != null && getProjectLevelConfigBaseFile().exists()) {
            conf += getSegmentComment("Appending from ${getProjectLevelConfigBaseFile().absolutePath}")
            conf += getProjectLevelConfigBaseFile().text
        } else {
            conf += getSegmentComment("Appending from ${getConfigBaseFile().absolutePath}")
            conf += getConfigBaseFile().text
        }
        if (getConfigAppendFile() != null && getConfigAppendFile().exists()) {
            conf += getSegmentComment("Appending from ${getConfigAppendFile().absolutePath}")
            conf += getConfigAppendFile().text
        }
        getConfigFile().text = conf
    }

    def doTaskAction() {
        project.javaexec {
            main "-jar"
            args "${getProGuardJar().absolutePath}"
            args "@${getConfigFile().absolutePath}"

            // Fail build if dex fails
            setIgnoreExitValue false

            // Set logging
            FileOutputStream ostream = new FileOutputStream(getLog());
            setErrorOutput(ostream)
            setStandardOutput(ostream)
        }
    }

    static def getSegmentComment(String comment) {
        int l = comment.length()
        StringBuilder b = new StringBuilder(l * 3 + 6 + 5)
        b.append("\n##")
        for (int i = 0; i < l; ++i)
            b.append('#')
        b.append("\n# ")
        b.append(comment)
        b.append("\n##")
        for (int i = 0; i < l; ++i)
            b.append('#')
        b.append("\n\n")
        b.toString()
    }

    public static String getTaskName(SourceSet sourceSet) {
        return BaseTask.composeTaskName(sourceSet.name, ProGuard.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = ProGuard.NAME
        final String ELEMENTS_DESC = '<SourceSet>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        return project.tasks.addRule("Pattern: $PATTERN: Creates a ProGuarded jar."
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
            ProGuard.create(project, sourceSet)
        })
    }

    public static ProGuard create(Project project, SourceSet sourceSet) {
        // Helpers
        final def sdk = project.moe.sdk

        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.name}"

        // Create task
        final String taskName = getTaskName(sourceSet)
        ProGuard proguardTask = project.tasks.create(taskName, ProGuard.class)
        proguardTask.description = "Generates ProGuarded jar files ($outPath)."

        // Add dependencies
        String classesTaskName
        String compileJavaTaskName
        if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            classesTaskName = JavaPlugin.CLASSES_TASK_NAME
            compileJavaTaskName = JavaPlugin.COMPILE_JAVA_TASK_NAME
        } else if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
            classesTaskName = JavaPlugin.TEST_CLASSES_TASK_NAME
            compileJavaTaskName = JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME
        } else {
            throw new GradleException("Unsupported SourceSet $sourceSet.name")
        }
        if (!project.moe.mavenProject && !project.hasProperty('moe.sources.custom')) {
            Task clsTask = project.tasks.getByName(classesTaskName)
            proguardTask.dependsOn clsTask
        }

        JavaCompile javaCompileTask = project.tasks.getByName(compileJavaTaskName);

        javaCompileTask.setSourceCompatibility("1.8");
        javaCompileTask.setTargetCompatibility("1.8");

        // Update convention mapping
        proguardTask.conventionMapping.configBaseFile = {
            sdk.getProGuardCfg()
        }
        proguardTask.conventionMapping.projectLevelConfigBaseFile = {
            BaseTask.fileIfExists(project, "proguard.cfg")
        }
        proguardTask.conventionMapping.configAppendFile = {
            BaseTask.fileIfExists(project, "proguard.append.cfg")
        }
        proguardTask.conventionMapping.inJars = {
            HashSet<File> inJars = new HashSet<>()
            if (!project.moe.mavenProject) {
                if (project.hasProperty('moe.sources.custom')) {
                    inJars.add(project.file(project.property('moe.sources.custom')))
                    return inJars
                }
                def compileConf = project.configurations[sourceSet.getCompileConfigurationName()]
                def compileConfFiles = compileConf.files(compileConf.dependencies.toArray(new Dependency[compileConf.dependencies.size()]))
                inJars.addAll(compileConfFiles)

                inJars.add(javaCompileTask.destinationDir)

            } else {
                String target = project.property("moe.mavenproject.outputdir")
                inJars.add(new File(target, 'classes'))
                if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.name)) {
                    inJars.add(new File(target, 'test-classes'))
                }
                new File(target, 'injars').listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(File dir, String name) {
                        return name.endsWith(".jar")
                    }
                }).each { inJars.add(it) }
            }

            if (!sdk.getFullTrim()) {
                inJars.removeAll(sdk.getMainJars().getFiles())
                if (project.hasProperty("moe.sdk.trim_ios") && sdk.getMainJars().getFiles().contains(sdk.getIOSJar())) {
                    inJars.add(sdk.getIOSJar())
                }
            }
            new ArrayList<>(inJars)
        }

        proguardTask.conventionMapping.libraryJars = {
            def list = new ArrayList<>()
            if (!project.hasProperty("moe.sdk.skip_java8support_jar")) {
                list.add(sdk.getJava8SupportJar())
            }
            if (!sdk.getFullTrim()) {
                list.addAll(sdk.getMainJars().getFiles())
                if (project.hasProperty("moe.sdk.trim_ios")) {
                    list.remove(sdk.getIOSJar())
                }
            }
            list
        }

        proguardTask.conventionMapping.proGuardJar = {
            sdk.getProGuardJar()
        }
        proguardTask.conventionMapping.outJar = {
            project.file("${project.buildDir}/${outPath}/proguarded.jar")
        }
        proguardTask.conventionMapping.configFile = {
            project.file("${project.buildDir}/${outPath}/proguard.cfg")
        }
        proguardTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/proguard.log")
        }
        return proguardTask
    }
}
