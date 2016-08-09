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

import org.moe.common.variant.ArchitectureVariant
import org.moe.common.variant.ModeVariant
import org.moe.common.variant.TargetVariant
import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.UnknownTaskException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

class XcodeProvider extends BaseTask {

	static String NAME = "XcodeProvider"

	/*
	Task inputs
	 */

	@InputFile
	File oatFile

	@InputFile
	File imageFile

	@OutputFile
	File oatFileLink

	@OutputFile
	File imageFileLink

	/*
	Task action
	 */

	@TaskAction
	void taskAction() {
		this.inputs
		project.logger.debug("|--- $name : $NAME ---|")
		project.logger.debug("|< oatFile: ${getOatFile()}")
		project.logger.debug("|< imageFile: ${getImageFile()}")
		project.logger.debug("|> oatFileLink: ${getOatFileLink()}")
		project.logger.debug("|> imageFileLink: ${getImageFileLink()}")

		copy(getOatFile(), getOatFileLink())
		copy(getImageFile(), getImageFileLink())
	}

	private void copy(File from, File to) {
		if (from == null && to == null) {
			return;
		}
		if (!from.exists()) {
			throw new GradleException("File doesn't exist: " + from)
		}
		if (to.exists()) {
			if (to.isDirectory()) {
				FileUtils.deleteDirectory(to)
			} else {
				if (!to.delete()) {
					throw new GradleException("Failed to delete file: " + to)
				}
			}
		}
		if (from.isDirectory()) {
			FileUtils.copyDirectory(from, to)
		} else {
			FileUtils.copyFile(from, to)
		}
	}

	public static String getTaskName(SourceSet sourceSet, ModeVariant modeVariant,
									 ArchitectureVariant arch, TargetVariant targetVariant) {
		return BaseTask.composeTaskName(sourceSet.name, modeVariant.name, arch.archName,
				targetVariant.platformName, XcodeProvider.NAME)
	}

	public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
		// Prepare constants
		final String TASK_NAME = XcodeProvider.NAME
		final String ELEMENTS_DESC = '<SourceSet><Mode><Architecture><Platform>'
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
			TaskUtil.assertSize(elements, 4, ELEMENTS_DESC)

			// Check element values & configure task on success
			SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
			ModeVariant modeVariant = ModeVariant.getModeVariant(elements[1])
			ArchitectureVariant architectureVariant = ArchitectureVariant.getArchitectureVariantByName(elements[2])
			TargetVariant targetVariant = TargetVariant.getTargetVariantByPlatformName(elements[3])
			XcodeProvider.create(project, sourceSet, modeVariant, architectureVariant, targetVariant)
		})
	}

	public static XcodeProvider create(Project project, SourceSet sourceSet, ModeVariant modeVariant,
									   ArchitectureVariant arch, TargetVariant targetVariant) {
		// Helpers
		final def sdk = project.moe.sdk

		// Construct default output path
		final String outPath = "${BasePlugin.MOE}/${sourceSet.name}/xcode/" +
				"${modeVariant.name}-${targetVariant.platformName}"

		// Create task
		final String taskName = getTaskName(sourceSet, modeVariant, arch, targetVariant)
		XcodeProvider providerTask = project.tasks.create(taskName, XcodeProvider.class)
		providerTask.description = "Generates object files ($outPath)."

		// Add dependencies
		final String dex2oatTaskName = Dex2Oat.getTaskName(sourceSet, modeVariant, arch.familyName)
		Dex2Oat dex2oatTask = (Dex2Oat) project.tasks.getByName(dex2oatTaskName)
		providerTask.dependsOn dex2oatTask

		final String startupProviderTaskName = StartupProvider.getTaskName(sourceSet)
		StartupProvider startupProviderTask = (StartupProvider) project.tasks.getByName(startupProviderTaskName)
		providerTask.dependsOn startupProviderTask

		final String resourcePackagerTaskName = BasePlugin.createTaskName(sourceSet.name, BasePlugin.RESOURCE_PACKAGER_TASK_NAME)
		Jar resourcePackagerTask = (Jar) project.tasks.getByName(resourcePackagerTaskName)
		providerTask.dependsOn resourcePackagerTask

        try {
            final String uiTransformerTaskName = UITransformer.getTaskName(sourceSet)
		    UITransformer uiTransformerTask = (UITransformer) project.tasks.getByName(uiTransformerTaskName)
			providerTask.dependsOn uiTransformerTask
		} catch (UnknownTaskException e) {
            // Skip UITransformer
        }


		if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
			final String classListProviderTaskName = TestClassesProvider.getTaskName(sourceSet)
			TestClassesProvider classListProviderTask = (TestClassesProvider) project.tasks.getByName(classListProviderTaskName)
			providerTask.dependsOn classListProviderTask
		}

		// Update convention mapping
		providerTask.conventionMapping.oatFile = {
			dex2oatTask.destOat
		}
		providerTask.conventionMapping.imageFile = {
			dex2oatTask.destImage
		}
		providerTask.conventionMapping.oatFileLink = {
			project.file("${project.buildDir}/${outPath}/${arch.getArchName()}.oat")
		}
		providerTask.conventionMapping.imageFileLink = {
			project.file("${project.buildDir}/${outPath}/${arch.getArchName()}.art")
		}

		return providerTask
	}
}
