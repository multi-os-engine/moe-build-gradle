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

public class PackagingOptions {

    private static final ArrayList<String> EXCLUDE_DEFAULT_NAMES = [
            'LICENSE', 'LICENSE.*', 'META-INF/LICENSE', 'META-INF/LICENSE.*',
            'NOTICE', 'NOTICE.*', 'META-INF/NOTICE', 'META-INF/NOTICE.*',
            '**/.*']

    @Input
    @Optional
    ArrayList<String> excludes = EXCLUDE_DEFAULT_NAMES;

    public PackagingOptions exclude(String... names) {
        excludes.addAll(names);
        return this;
    }

}
