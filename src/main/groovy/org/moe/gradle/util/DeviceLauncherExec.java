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

package org.moe.gradle.util;

import org.moe.common.exec.AbstractJarExec;
import org.moe.common.sdk.MOESDK;

import java.io.File;
import java.nio.file.InvalidPathException;

public class DeviceLauncherExec extends AbstractJarExec {

    static final File LAUNCHER_FILE;
    static final File WORKING_DIR;

    static {
        try {
            LAUNCHER_FILE = new File(MOESDK.SDK_TOOLS_PATH + File.separator + MOESDK.IOS_DEVICE_JAR);
            WORKING_DIR = new File(MOESDK.SDK_TOOLS_PATH);
        } catch (InvalidPathException e) {
            throw new RuntimeException("Failed to load device launcher jar. " + e.getMessage());
        }
    }

    public DeviceLauncherExec() {
        super(LAUNCHER_FILE, WORKING_DIR);
    }

}
