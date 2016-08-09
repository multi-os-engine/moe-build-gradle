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
import org.moe.gradle.util.OsCheckUtil
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

import java.nio.file.Path
import java.nio.file.Paths

class OutsidePackager extends Zip {

    static String NAME = "OutsidePackager"

    /*
    Task outputs
     */

    @OutputFiles
    Collection<File> dexFiles


    public static String getTaskName(SourceSet sourceSet, ModeVariant modeVariant, TargetVariant targetVariant) {
        return BaseTask.composeTaskName(sourceSet.name, modeVariant.name, targetVariant.platformName, NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = NAME
        final String ELEMENTS_DESC = '<SourceSet><Mode><Platform>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Prepare archive for Remote or Cloud builds."
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

            create(project, sourceSet, modeVariant, targetVariant)
        })
    }

    public static OutsidePackager create(Project project, SourceSet sourceSet, ModeVariant modeVariant, TargetVariant targetVariant) {
        File baseDir = project.getProjectDir()

        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.name}/outside/" +
                "${modeVariant.name}-${TargetVariant.IOS_DEVICE_PLATFORM_NAME}"

        // Create task
        final String taskName = getTaskName(sourceSet, modeVariant, targetVariant)
        OutsidePackager outsidePackager = project.tasks.create(taskName, OutsidePackager.class)
        outsidePackager.description = "Prepare archive for remote or cloud build ($outPath)."
        outsidePackager.duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // Update settings
        outsidePackager.setDestinationDir(project.file("${project.buildDir}/${outPath}"))
        outsidePackager.setArchiveName("outside.zip")

        // Add dependencies
        final String dexTaskName = Dex.getTaskName(sourceSet, modeVariant)
        Dex dexTask = (Dex) project.tasks.getByName(dexTaskName)
        outsidePackager.dependsOn dexTask
        outsidePackager.
                from(dexTask.outputs.files.find {
                    it.absolutePath.endsWith('.jar')
                }) {
                    dexTask.outputs.files.findAll {
                        it.absolutePath.endsWith('.jar')
                    }.each {
                        into(getRelativePath(baseDir, it.getParentFile()))
                    }
                }
        outsidePackager.conventionMapping.dexFiles = {
            def files = [
                    dexTask.outputs.files.find {
                        it.absolutePath.endsWith('.jar')
                    }
            ]
            files
        }

        // preregister.txt
        final String startupProviderTaskName = StartupProvider.getTaskName(sourceSet)
        StartupProvider startupProviderTask = (StartupProvider) project.tasks.getByName(startupProviderTaskName)
        outsidePackager.dependsOn startupProviderTask
        File fileToCopy = startupProviderTask.preregisterFile
        outsidePackager.
                from(fileToCopy) {
                    into(getRelativePath(baseDir, fileToCopy.getParentFile()))
                }

        // application.jar
        final String resourcePackagerTaskName = BasePlugin.createTaskName(sourceSet.name, BasePlugin.RESOURCE_PACKAGER_TASK_NAME)
        Jar resourcePackagerTask = (Jar) project.tasks.getByName(resourcePackagerTaskName)
        outsidePackager.dependsOn resourcePackagerTask
        fileToCopy = resourcePackagerTask.getArchivePath()
        outsidePackager.
                from(fileToCopy) {
                    into(getRelativePath(baseDir, fileToCopy.getParentFile()))
                }

        // MainUI.storyboard
        try {
            final String uiTransformerTaskName = UITransformer.getTaskName(sourceSet)
            UITransformer uiTransformerTask = (UITransformer) project.tasks.getByName(uiTransformerTaskName)
            outsidePackager.dependsOn uiTransformerTask
            fileToCopy = uiTransformerTask.outputFile
            outsidePackager.
                    from(fileToCopy) {
                        into(getRelativePath(baseDir, fileToCopy.getParentFile()))
                    }
        } catch (UnknownTaskException e) {
            // Skip UITransformer
        }


        // Xcode project generation
        String projectGeneratorName = XcodeProjectGenerator.getTaskName()
        XcodeProjectGenerator projectGenerator = project.tasks.getByName(projectGeneratorName)
        outsidePackager.dependsOn projectGenerator

        // validate that resources are located under the module folder
        // Copy resources, MainUI.storyboard, Info.plist
        copyCustomResources(projectGenerator, outsidePackager, sourceSet, baseDir.getAbsolutePath())

        // test classes
        if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
            final String classListProviderTaskName = TestClassesProvider.getTaskName(sourceSet)
            TestClassesProvider classListProviderTask = (TestClassesProvider) project.tasks.getByName(classListProviderTaskName)
            outsidePackager.dependsOn classListProviderTask
            fileToCopy = classListProviderTask.classListFile
            outsidePackager.
                    from(fileToCopy) {
                        into(getRelativePath(baseDir, fileToCopy.getParentFile()))
                    }
        }

        // sourceSet.resources
        sourceSet.resources.each { resource ->
            outsidePackager.
                    from(resource) {
                        // TODO: excluding not working
                        exclude 'layout/**'
                        into getRelativePath(baseDir, resource.getParentFile())
                    }
        }

        // xcode folder
        outsidePackager.
                from(projectGenerator.xcodeProjectDir) {
                    into("build/xcode/")
                }

        // TODO: excluding not working
        outsidePackager.exclude('**/MANIFEST/**')


        return outsidePackager
    }

    private static String getRelativePath(File base, File second) {
        return base.toURI().relativize(second.toURI()).getPath();
    }

    private static void copyCustomResources(XcodeProjectGenerator projectGenerator, OutsidePackager outsidePackager, SourceSet sourceSet, String projectDirPath) {

        Set<String> collection = [projectGenerator.mainUIStoryboardPath, projectGenerator.infoPlistPath, projectGenerator.launchScreenFilePath]
//        println(collection)


        collection.addAll(sourceSet.resources.srcDirs.collect() { file -> file.getAbsolutePath() })
        collection.findAll { path -> path != null && !path.empty }
                .each { path ->

            String checkPathString = getOsSpecificPathFromOSXPath(path);

            // If path is not absolute -> we can't compare it -> try to get absolute path relative to xcode project
            Path checkPath = Paths.get(checkPathString)
            if (!checkPath.absolute) {
                checkPathString =
                        Paths.get(getOsSpecificPathFromOSXPath(projectGenerator.xcodeProjectDir.absolutePath)
                                + File.separator
                                + checkPathString).normalize().toString()
            }


            if (!checkPathString.startsWith(projectDirPath)) {
                String[] splitPath = checkPathString.split(File.pathSeparator)
                String failedFile = splitPath.length > 0 ? splitPath.last() : checkPathString

                throw new GradleException(failedFile + " must be located only inside module folder")
            }

            // Copy MainUI.storyboard, info.plist, launchsreen.xib
            File fileToCopy = new File(checkPathString)
            if (fileToCopy.isFile()) {
                outsidePackager.
                        from(fileToCopy) {
                            into(getRelativePath(new File(projectDirPath), fileToCopy.getParentFile()))
                        }
            }
        }
    }

    private static String getOsSpecificPathFromOSXPath(String primaryPath) {
        if (OsCheckUtil.getOperatingSystemType() == OsCheckUtil.OSType.Windows) {
            return primaryPath.replaceAll("/", "\\\\")
        }
        return primaryPath
    }
}
