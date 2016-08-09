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

class ExecUtil {

    static File run(Project project, String exec, String... pargs) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        try {
            project.exec {
                executable exec
                pargs.each {
                    args it
                }
                setStandardOutput(baos)
            }
        } catch (Exception e) {
            throw new GradleException("Calling ${exec} failed, ${e.getMessage()}")
        }

        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()))
        String str = reader.readLine()
        if (str == null || str.length() == 0) {
            throw new GradleException("Calling ${exec} failed, result is null")
        }

        baos.close()
        reader.close()

        new File(new String(str))
    }
}
