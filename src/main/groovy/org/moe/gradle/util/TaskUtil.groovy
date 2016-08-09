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

import org.moe.gradle.variant.InfoTaskVariant
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

class TaskUtil {

    public static void assertSize(List<String> elements, int count, String desc) {
        if (elements.size() != count) {
            String[] tmp = elements.toArray(new String[elements.size()])
            throw new GradleException("Invalid value for '$desc': '${StringUtil.camelcase(tmp)}'")
        }
    }

    public static SourceSet getSourceSet(JavaPluginConvention javaConvention, String sourceSetName) {
        SourceSet sourceSet = javaConvention.sourceSets.getByName(sourceSetName)
        if (sourceSet == null) {
            // Fail on unsupported element
            throw new GradleException("Invalid SourceSet '$sourceSetName'")
        }
        sourceSet
    }

    public static String getRequiredEnvVar(String name, String defaultValue = null) {
        String value = System.getenv().get(name)
        if (value == null || value.length() == 0) {
            value = defaultValue
        }
        if (value == null || value.length() == 0) {
            throw new GradleException("Unspecified environment variable '$name'")
        }
        value
    }

    public static ArrayList<String> valuesFromCSV(String input, String provider) {
        if (input == null || input.length() == 0) {
            throw new GradleException("Illegal list provided by $provider")
        }
        def list = input.split(",")
        if (list == null || list.size() == 0) {
            throw new GradleException("Illegal list provided by $provider")
        }
        return list
    }

    public static InfoTaskVariant getInfoTaskVariant(String name) {
        InfoTaskVariant infoTaskVariant = InfoTaskVariant.getByName(name)
        if (infoTaskVariant == null) {
            // Fail on unsupported element
            throw new GradleException("Invalid InfoTaskVariant '$name'")
        }
        infoTaskVariant
    }
}
