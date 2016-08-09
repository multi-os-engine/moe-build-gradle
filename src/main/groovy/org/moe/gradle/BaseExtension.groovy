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

package org.moe.gradle

import org.moe.common.utils.FileUtil
import org.moe.gradle.option.*
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.eclipse.model.FileReference

class BaseExtension {

    private final Project project

    final MOESDKOptions sdk
    final Dex2OatOptions dex2oatOptions
    final XcodeOptions xcode
    final ResourceOptions resources
    final PackagingOptions packagingOptions
    final IpaOptions ipaOptions
    String mainClassName

    boolean mavenProject = false

    protected BaseExtension(BasePlugin plugin, ProjectInternal project, Instantiator instantiator) {
        this.project = project
        sdk = instantiator.newInstance(MOESDKOptions.class, project)
        dex2oatOptions = instantiator.newInstance(Dex2OatOptions.class)
        xcode = instantiator.newInstance(XcodeOptions.class)
        validateMainTarget(xcode)
        resources = instantiator.newInstance(ResourceOptions.class)
        packagingOptions = instantiator.newInstance(PackagingOptions.class)
        ipaOptions = instantiator.newInstance(IpaOptions.class)
        validateIpaOptions()
    }

    void dex2oatOptions(Action<Dex2OatOptions> action) {
        action.execute(dex2oatOptions)
    }

    void xcode(Action<XcodeOptions> action) {
        action.execute(xcode)
    }

    void resources(Action<ResourceOptions> action) {
        action.execute(resources)
    }

    void packagingOptions(Action<PackagingOptions> action) {
        action.execute(packagingOptions)
    }

    void ipaOptions(Action<IpaOptions> action) {
        action.execute(ipaOptions)
    }

    private void validateMainTarget(XcodeOptions xcodeOptions) {
        String mainTarget = xcodeOptions.mainTarget
        if ((mainTarget == null) || mainTarget.isEmpty()) {
            xcodeOptions.mainTarget = project.getName()
        }
    }

    private void validateIpaOptions() {
        // Console arguments have higher priority
        if (project.hasProperty("moe.ipaOptions.signingIdentity")) {
            ipaOptions.signingIdentity = project.property("moe.ipaOptions.signingIdentity")
        }
        if (project.hasProperty("moe.ipaOptions.provisioningProfile")) {
            ipaOptions.provisioningProfile = project.property("moe.ipaOptions.provisioningProfile")
        }
    }

//    void setMavenProject(boolean value) {
//        mavenProject = value
//        if (value == true) {
//            if (project.hasProperty("moe.mavenproject.no_init")) {
//                return
//            }
//            if (!project.hasProperty("moe.mavenproject.platform")) {
//                throw new GradleException("Maven projects require the moe.mavenproject.platform property")
//            }
//            if (!project.hasProperty("moe.mavenproject.sdk")) {
//                throw new GradleException("Maven projects require the moe.mavenproject.sdk property")
//            }
//            if (!project.hasProperty("moe.mavenproject.outputdir")) {
//                throw new GradleException("Maven projects require the moe.mavenproject.outputdir property")
//            }
//            sdk.maven(project.property("moe.mavenproject.platform"), project.property("moe.mavenproject.sdk"))
//        }
//    }

//    void fixJavaDocAndSources(Classpath cp) {
//        cp.entries.each {
//            if (it instanceof Library) {
//                Library lib = it;
//                if (lib.library.file == sdk.mavenSDK.core) {
//                    lib.exported = false
//                    /*File sources = sdk.getJavaJar("main", "", "sources")
//                    if (sources.exists()) {
//                        lib.sourcePath = new EclipseFileReference(sources)
//                    }*/
//                    lib.javadocPath = new EclipseFileReference(sdk.mavenSDK.coreJavadoc)
//                } else if (lib.library.file == sdk.mavenSDK.platform) {
//                    lib.exported = false
//                    /*File sources = sdk.getJavaJar("main", "", "sources")
//                    if (sources.exists()) {
//                        lib.sourcePath = new EclipseFileReference(sources)
//                    }*/
//                    lib.javadocPath = new EclipseFileReference(sdk.mavenSDK.platformJavadoc)
//                } else if (lib.library.file == sdk.mavenSDK.junit) {
//                    lib.exported = false
//                    /*File sources = sdk.getJavaJar("test", "test", "sources")
//                    if (sources.exists()) {
//                        lib.sourcePath = new EclipseFileReference(sources)
//                    }*/
//                    lib.javadocPath = new EclipseFileReference(sdk.mavenSDK.junitJavadoc)
//                }
//            }
//        }
//    }

    /**
     * Custom FileReference implementation because they had to make it internal...
     */
    private static class EclipseFileReference implements FileReference {

        final File file
        final String path

        EclipseFileReference(File file) {
            this.file = file
            this.path = file.absolutePath
        }

        @Override
        String getJarURL() {
            return "jar:${file.toURI()}!/"
        }

        @Override
        boolean isRelativeToPathVariable() {
            return false
        }

        @Override
        int hashCode() {
            return file.hashCode()
        }
    }
}
