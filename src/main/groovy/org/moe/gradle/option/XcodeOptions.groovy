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

package org.moe.gradle.option

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

public class XcodeOptions {

    @Input
    Boolean generateProject = true

    @Input
    String mainTarget

    @Input
    String testTarget = ""

    @Input
    String mainProductName = null

    @Input
    String testProductName = null

    @Input
    String mainUIStoryboardPath = ""

    @Optional
    @Input
    String launchScreenFilePath = null

    @Optional
    @Input
    String launchImagesSource = null

    @Optional
    @Input
    String locationWhenInUseUsageDescription = null

    @Optional
    @Input
    String appIconsSource = null

    @Input
    String organizationName = ""

    @Input
    String companyIdentifier = ""

    @Input
    String bundleID = ""

    @Input
    String packageName = ""

    @Input
    String infoPlistPath = ""

    @Input
    String testInfoPlistPath = ""

    @Input
    Boolean applicationExitOnSuspend = false

    @Input
    String bundleShortVersionString = ""

    @Input
    String bundleVersion = ""

    @Input
    String deploymentTarget = ""

    @Input
    @Optional
    String xcodeProjectDirPath = null

    @Input
    List<String> supportedInterfaceOrientations

    String getMainProductName() {
        if (mainProductName == null) {
            return mainTarget
        }
        return mainProductName
    }

    String getTestProductName() {
        if (testProductName == null) {
            return testTarget
        }
        return testProductName
    }
}
