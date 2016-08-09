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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency

class MavenSDK {

    /*
     * MOE PLATFORM
     */

    /**
     * moe-<platform>-core.jar
     */
    final File core

    /**
     * moe-<platform>-core-javadoc.jar
     */
    final File coreJavadoc

    /**
     * moe-<platform>.jar
     */
    final File platform

    /**
     * moe-<platform>-javadoc.jar
     */
    final File platformJavadoc

    /**
     * moe-<platform>-junit.jar
     */
    final File junit

    /**
     * moe-<platform>-junit-javadoc.jar
     */
    final File junitJavadoc

    /**
     * moe-<platform>-tools.zip
     */
    final File tools

    /**
     * moe-<platform>-runtime.zip
     */
    final File runtime

    /*
     * MOE PLATFORM DEBUG
     */

    /**
     * moe-<platform>-debug-runtime-dsym.zip (Optional)
     */
    final File runtimeDsym

    /**
     * moe-<platform>-debug-runtime-debug.zip (Optional)
     */
    final File runtimeDebug

    /**
     * moe-<platform>-debug-tools-dsym.zip (Optional)
     */
    final File toolsDsym

    /**
     * moe-<platform>-debug-tools-debug.zip (Optional)
     */
    final File toolsDebug

    private MavenSDK(File core, File coreJavadoc, File platform, File platformJavadoc,
                     File junit, File junitJavadoc, File tools, File runtime,
                     File runtimeDsym, File runtimeDebug,
                     File toolsDsym, File toolsDebug) {
        if (core == null || coreJavadoc == null
                || platform == null || platformJavadoc == null || junit == null
                || junitJavadoc == null || tools == null || runtime == null) {
            throw new NullPointerException()
        }
        // MOE PLATFORM
        this.core = core
        this.coreJavadoc = coreJavadoc
        this.platform = platform
        this.platformJavadoc = platformJavadoc
        this.junit = junit
        this.junitJavadoc = junitJavadoc
        this.tools = tools
        this.runtime = runtime

        // MOE PLATFORM DEBUG
        this.runtimeDsym = runtimeDsym
        this.runtimeDebug = runtimeDebug
        this.toolsDsym = toolsDsym
        this.toolsDebug = toolsDebug
    }

    private static class Artifact {
        final String suffix
        File path

        Artifact(String suffix) {
            this.suffix = suffix
        }

        void updateFile(Set<File> files) {
            path = files.find {
                it.name.endsWith(suffix)
            }
        }
    }

    private static Artifact addArtifact(List<Artifact> artifacts,
                                        ExternalModuleDependency dep, String _name,
                                        String _type, String _classifier) {
        dep.artifact { name = _name; type = _type; classifier = _classifier }
        def tmp = new Artifact("-${_classifier}.${_type}")
        artifacts.add(tmp)
        tmp
    }

    public static MavenSDK create(Project project, Configuration conf, Dependency dep,
                                  String artifactID, String classifierPrefix, boolean debug) {
        def artifacts = []

        // Debug dependencies
        Artifact runtimeDsym = null
        Artifact runtimeDebug = null
        Artifact toolsDsym = null
        Artifact toolsDebug = null
        def debugDep = null
        if (debug) {
            def debugDepStr = "$dep.group:$dep.name-debug:$dep.version"
            debugDep = project.dependencies.create(debugDepStr)
            def debugArtifactID = "$artifactID-debug"
            conf.dependencies.add(debugDep)
            runtimeDsym = addArtifact(artifacts, debugDep, debugArtifactID, 'zip', 'runtime-dsym')
            runtimeDebug = addArtifact(artifacts, debugDep, debugArtifactID, 'zip', 'runtime-debug')
            toolsDsym = addArtifact(artifacts, debugDep, debugArtifactID, 'zip', 'tools-dsym')
            toolsDebug = addArtifact(artifacts, debugDep, debugArtifactID, 'zip', 'tools-debug')
        }

        // Default dependencies
        Artifact core = addArtifact(artifacts, dep, artifactID, 'jar', 'core')
        Artifact corejd = addArtifact(artifacts, dep, artifactID, 'jar', 'core-javadoc')
        Artifact bind = addArtifact(artifacts, dep, artifactID, 'jar', "${classifierPrefix}")
        Artifact bindjd = addArtifact(artifacts, dep, artifactID, 'jar', "${classifierPrefix}-javadoc")
        Artifact junit = addArtifact(artifacts, dep, artifactID, 'jar', 'junit')
        Artifact junitjd = addArtifact(artifacts, dep, artifactID, 'jar', 'junit-javadoc')
        Artifact tools = addArtifact(artifacts, dep, artifactID, 'zip', 'tools')
        Artifact runtime = addArtifact(artifacts, dep, artifactID, 'zip', 'runtime')

        def fileSet = debugDep == null ? conf.files(dep) : conf.files(dep, debugDep)
        artifacts.each {
            it.updateFile(fileSet)
        }

        return new MavenSDK(core.path, corejd.path, bind.path, bindjd.path, junit.path,
                junitjd.path, tools.path, runtime.path,
                (runtimeDsym == null ? null : runtimeDsym.path),
                (runtimeDebug == null ? null : runtimeDebug.path),
                (toolsDsym == null ? null : toolsDsym.path),
                (toolsDebug == null ? null : toolsDebug.path))
    }

}
