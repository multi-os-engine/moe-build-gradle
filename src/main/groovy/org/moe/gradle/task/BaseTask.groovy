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
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project

class BaseTask extends DefaultTask {

    private boolean shouldLogOnExecFail() {
        if (!project.hasProperty("moe.tasks.printLogOnFail")) {
            return true
        }
        return project.property("moe.tasks.printLogOnFail") == true
    }

    protected void securedLoggableAction(File log, Closure c) {
        if (!log.exists()) {
            log.text = ""
        }
        boolean failed = true
        try {
            c()
            failed = false
        } finally {
            if (failed && shouldLogOnExecFail() && log != null) {
                System.err.print("\n" +
                        "###########\n" +
                        "# ERROR LOG\n" +
                        "###########\n\n")
                IOUtils.copy(new FileInputStream(log), System.err)
            }
        }
    }

    protected static String composeTaskName(String... components) {
        String[] cmp2 = new String[components.length + 1]
        System.arraycopy(components, 0, cmp2, 1, components.length)
        cmp2[0] = BasePlugin.MOE
        StringUtil.camelcase(cmp2)
    }

    protected static File fileIfExists(Project project, String filename) {
        File f = project.file(filename);
        return f.exists() ? f : null;
    }

}
