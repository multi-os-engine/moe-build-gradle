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

class Dex2OatOptions {

    private static final List<String> BACKENDS = ['Optimizing']

    @Input
    private String compilerBackend = BACKENDS[0]

    String getCompilerBackend() {
        return compilerBackend
    }

    void setCompilerBackend(String compilerBackend) {
        if (!BACKENDS.contains(compilerBackend)) {
            throw new IllegalArgumentException("Unsupported compiler backend $compilerBackend.\n" +
                    "Supported backends are: $BACKENDS.toListString()")
        }
        this.compilerBackend = compilerBackend
    }

    @Override
    String toString() {
        return "{ compilerBackend=$compilerBackend }"
    }
}
