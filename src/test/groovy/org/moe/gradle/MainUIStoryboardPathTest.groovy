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

package org.moe.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static org.moe.gradle.task.XcodeProjectGenerator.getMainUIStoryboardPath


class MainUIStoryboardPathTest extends GroovyTestCase {

    Project project = ProjectBuilder.builder().build();

    void testReturnsUITransformerOutputIfCustomPathNotSetAndTransformerNotSkipped() {
        File uiTransformerOutput = new File(File.createTempDir(), "MainUI.storyboard")
        uiTransformerOutput.createNewFile()
        boolean skipTransformerTask = false

        String actual = getMainUIStoryboardPath(project, '', uiTransformerOutput, skipTransformerTask)
        assertEquals(uiTransformerOutput.absolutePath, actual)

        actual = getMainUIStoryboardPath(project, null, uiTransformerOutput, skipTransformerTask)
        assertEquals(uiTransformerOutput.absolutePath, actual)
    }

    void testReturnsNonExistentPathIfCustomPathNotSetAndTransformerOutputDoesNotExist() {
        File uiTransformerOutput = new File(File.createTempDir(), "MainUI.storyboard")
        boolean skipTransformerTask = false

        String actual = getMainUIStoryboardPath(project, '', uiTransformerOutput, skipTransformerTask)
        assertEquals(uiTransformerOutput.absolutePath, actual)

        actual = getMainUIStoryboardPath(project, null, uiTransformerOutput, skipTransformerTask)
        assertEquals(uiTransformerOutput.absolutePath, actual)
    }

    void testReturnsDefaultPathIfCustomPathNotSetAndTransformerSkipped() {
        File defaultStoryboardDir = new File(project.getProjectDir(), "src/main/resources/")
        defaultStoryboardDir.mkdirs()
        File defaultStoryboardLocation = new File(defaultStoryboardDir, "MainUI.storyboard")
        defaultStoryboardLocation.createNewFile()

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        String actual = getMainUIStoryboardPath(project, "", uiTransformerOutput, skipTransformerTask)
        assertEquals(defaultStoryboardLocation.getAbsolutePath(), actual)

        actual = getMainUIStoryboardPath(project, null, uiTransformerOutput, skipTransformerTask)
        assertEquals(defaultStoryboardLocation.getAbsolutePath(), actual)
    }

    void testReturnsNullIfCustomPathNotSetAndTransformerSkippedAndDefaultPathDoesNotExist() {
        File defaultStoryboardDir = new File(project.getProjectDir(), "src/main/resources/MainUI.storyboard")
        defaultStoryboardDir.delete()

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        String actual = getMainUIStoryboardPath(project, "", uiTransformerOutput, skipTransformerTask)
        assertEquals(null, actual)

        actual = getMainUIStoryboardPath(project, null, uiTransformerOutput, skipTransformerTask)
        assertEquals(null, actual)
    }

    void testReturnsNullIfCustomPathNotSetAndTransformerSkipped() {
        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        String actual = getMainUIStoryboardPath(project, "", uiTransformerOutput, skipTransformerTask)
        assertEquals(null, actual)

        actual = getMainUIStoryboardPath(project, null, uiTransformerOutput, skipTransformerTask)
        assertEquals(null, actual)
    }

    void testReturnsFullCustomPathIfFullCustomPathSetAndTransformerSkipped() {
        File customStoryboardDir = new File(project.getProjectDir(), "my/custom/path/")
        customStoryboardDir.mkdirs()
        File customStoryboardLocation = new File(customStoryboardDir, "Custom.storyboard")
        customStoryboardLocation.createNewFile()
        String storyboardPath = customStoryboardLocation.absolutePath

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        String actual = getMainUIStoryboardPath(project, storyboardPath, uiTransformerOutput, skipTransformerTask)

        assertEquals(storyboardPath, actual)
    }

    void testReturnsFullCustomPathIfRelativeCustomPathSetAndTransformerSkipped() {
        File customStoryboardDir = new File(project.getProjectDir(), "my/custom/path/")
        customStoryboardDir.mkdirs()
        File customStoryboardLocation = new File(customStoryboardDir, "Custom.storyboard")
        customStoryboardLocation.createNewFile()
        String storyboardPath = "my/custom/path/Custom.storyboard"

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        String actual = getMainUIStoryboardPath(project, storyboardPath, uiTransformerOutput, skipTransformerTask)

        assertEquals(customStoryboardLocation.getAbsolutePath(), actual)
    }

    void testThrowsExceptionIfCustomPathSetAndTransformerNotSkipped() {
        String storyboardPath = File.createTempFile("Custom", ".storyboard")
        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = false

        def msg = shouldFail(GradleException) {
            getMainUIStoryboardPath(project, storyboardPath, uiTransformerOutput, skipTransformerTask)
        }
        String expected = "Internal error: uiTransformer should not be called if storyboard path isn't empty!"
        assertEquals(expected, msg)
    }

    void testThrowsExceptionIfCustomPathSetAndTransformerSkippedAndCustomPathDoesNotExist() {
        File customStoryboardDir = new File(project.getProjectDir(), "my/custom/path/")
        customStoryboardDir.mkdirs()
        File customStoryboardLocation = new File(customStoryboardDir, "Custom.storyboard")
        String storyboardPath = customStoryboardLocation.absolutePath

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        def msg = shouldFail(GradleException) {
            getMainUIStoryboardPath(project, storyboardPath, uiTransformerOutput, skipTransformerTask)
        }
        String expected = "Custom storyboard path can't be found here: " + customStoryboardLocation.getAbsolutePath()
        assertEquals(expected, msg)
    }

    void testThrowsExceptionIfCustomPathSetAndTransformerSkippedAndStoryboardNameIsNotCorrect() {
        File customStoryboardDir = new File(project.getProjectDir(), "my/custom/path/")
        customStoryboardDir.mkdirs()
        File customStoryboardLocation1 = new File(customStoryboardDir, "MainUI.incorrect_storyboard")
        customStoryboardLocation1.createNewFile()
        String storyboardPath1 = customStoryboardLocation1.absolutePath
        File customStoryboardLocation2 = new File(customStoryboardDir, ".incorrect.storyboard.MainUI")
        customStoryboardLocation2.createNewFile()
        String storyboardPath2 = customStoryboardLocation2.absolutePath
        File customStoryboardLocation3 = new File(customStoryboardDir, "MainUIstoryboard")
        customStoryboardLocation3.createNewFile()
        String storyboardPath3 = customStoryboardLocation3.absolutePath

        File uiTransformerOutput = File.createTempDir()
        boolean skipTransformerTask = true

        def msg = shouldFail(GradleException) {
            getMainUIStoryboardPath(project, storyboardPath1, uiTransformerOutput, skipTransformerTask)
        }
        String expected = "Storyboard name doesn't contain .storyboard extension at path: " + customStoryboardLocation1.getAbsolutePath()
        assertEquals(expected, msg)

        msg = shouldFail(GradleException) {
            getMainUIStoryboardPath(project, storyboardPath2, uiTransformerOutput, skipTransformerTask)
        }
        expected = "Storyboard name is incorrect at path: " + customStoryboardLocation2.getAbsolutePath()
        assertEquals(expected, msg)

        msg = shouldFail(GradleException) {
            getMainUIStoryboardPath(project, storyboardPath3, uiTransformerOutput, skipTransformerTask)
        }
        expected = "Storyboard name doesn't contain .storyboard extension at path: " + customStoryboardLocation3.getAbsolutePath()
        assertEquals(expected, msg)
    }
}
