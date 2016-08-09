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

import org.apache.commons.lang.StringUtils

class StringUtil {

    public static String camelcase(String... components) {
        StringBuilder builder = new StringBuilder();
        for (String comp in components) {
            if (comp == null) {
                continue
            }
            if (builder.length() == 0) {
                builder.append(StringUtils.uncapitalize(comp))
            } else {
                builder.append(comp.capitalize())
            }
        }
        builder.toString()
    }

    static List<String> getElemsInRule(String rule, String prefix, String suffix) {
        if (!rule.startsWith(prefix) || !rule.endsWith(suffix)) {
            return null
        }

        final String base = rule.substring(prefix.length(), rule.length() - suffix.length())
        if (base.length() == 0) {
            return []
        }

        List<String> words = []
        int start = 0
        int stop = 1

        while (stop < base.length()) {
            if (Character.isUpperCase(base.charAt(stop))) {
                words.add(base.substring(start, stop).toLowerCase())
                start = stop
            }

            stop++
            if (stop >= base.length()) {
                words.add(base.substring(start, stop).toLowerCase())
            }
        }

        return words
    }
}
