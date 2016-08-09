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

import org.moe.common.sdk.MOESDK
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

public class MOESDKOptions {

    private MOESDK moeSDK = null
    private final Project project
    private String platform

    MOESDKOptions(Project project) {
        this.project = project
        platform = project.hasProperty("moe.platform") ?
                project.property("moe.platform") :
                'ios'
        moeSDK = new MOESDK()
        project.logger.debug("MOE SDK set up complete")
    }

    public boolean getFullTrim() {
        moeSDK == null ? null : (moeSDK.tools.fullTrim || project.hasProperty("moe.sdk.trim_all"))
    }

    public FileCollection getMainJars() {
        if (project.hasProperty('moe.sdk.skip_ios')) {
            moeSDK == null ? project.files() : project.files(moeSDK.bindings.coreJar())
        } else {
            moeSDK == null ? project.files() : project.files(moeSDK.bindings.coreJar(), moeSDK.bindings.iosJar())
        }
    }

    public FileCollection getMainDexFiles() {
        if (project.hasProperty('moe.sdk.skip_ios')) {
            moeSDK == null ? project.files() : project.files(moeSDK.bindings.coreDex())
        } else {
            moeSDK == null ? project.files() : project.files(moeSDK.bindings.coreDex(), moeSDK.bindings.iosRetroDex())
        }
    }

    public File getDxExec() {
        moeSDK == null ? null : new File(moeSDK.tools.dxExec().getParentFile(), "jack");
    }

    public File getDex2OatExec() {
        moeSDK == null ? null : moeSDK.tools.dex2OatExec()
    }

    public File getProGuardJar() {
        moeSDK == null ? null : moeSDK.tools.proGuardJar()
    }

    public File getRetrolambdaJar() {
        moeSDK == null ? null : moeSDK.tools.retrolambdaJar()
    }

    public File getProGuardCfg() {
        if (getFullTrim()) {
            moeSDK == null ? null : moeSDK.tools.proGuardFullCfg()
        } else {
            moeSDK == null ? null : moeSDK.tools.proGuardCfg()
        }
    }

    public File getUITransformerJar() {
        moeSDK == null ? null : moeSDK.tools.uiTransformerJar()
    }

    public File getUITransformerRes() {
        moeSDK == null ? null : moeSDK.tools.uiTransformerRes()
    }

    public File getUiResourcesDir() {
        return new File(this.project.getProjectDir().getAbsolutePath() + "/src/main/resources")
    }

    public File getIOSJar() {
        moeSDK == null ? null : moeSDK.bindings.iosJar()
    }

    public File getIOSDex() {
        moeSDK == null ? null : moeSDK.bindings.iosRetroDex()
    }

    public File getJava8SupportJar() {
        moeSDK == null ? null : moeSDK.tools.java8SupportJar()
    }
//
//    public FileCollection getTestJars() {
//        moeSDK == null ? project.files() : project.files(moeSDK.bindings.iosJunitJar())
//    }
//
    public String getCoreJarPath() {
        moeSDK == null ? null : moeSDK.bindings.coreJar().absolutePath
    }
//
//    public String getTestJarPath() {
//        moeSDK == null ? null : moeSDK.bindings.iosJunitJar().absolutePath
//    }

    public boolean isIOS() {
        return 'ios' == platform
    }

    public boolean isTvOS() {
        return 'tvos' == platform
    }
}
