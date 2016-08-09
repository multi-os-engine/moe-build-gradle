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

package org.moe.gradle.util

import org.gradle.api.GradleException
import org.gradle.api.Project

public class XcodeUtil {

    /**
     * Holder class for the shared instance.
     */
    private static class HOLDER {
        static final XcodeUtil INSTANCE = new XcodeUtil()
    }

    /**
     * Returns the shared instance of this class.
     * @return shared instance
     */
    public static get() {
        return HOLDER.INSTANCE
    }

    /*
    Executable names.
     */
    private static String WHICH = "which"
    private static String XCODEBUILD_EXEC = "xcodebuild"

    /**
     * Xcodebuild's path.
     */
    private File xcodebuild

    /**
     * Initializes the utility class.
     * @param project current project
     */
    void initialize(Project project) {
        xcodebuild = ExecUtil.run(project, WHICH, XCODEBUILD_EXEC)
    }

    /**
     * Returns the path to xcodebuild.
     * @return path to xcodebuild
     */
    public File getXcodeBuildPath() {
        check(xcodebuild, XCODEBUILD_EXEC)
        xcodebuild
    }

    /**
     * Checks whether file is null and fails if it is.
     * @param file file to check
     * @param exec executable name
     */
    private static void check(File file, String exec) {
        if (file == null) {
            throw new GradleException("Executable for '$exec' was not found")
        }
    }
}
