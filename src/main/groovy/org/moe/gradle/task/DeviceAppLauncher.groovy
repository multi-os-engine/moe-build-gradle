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

import org.gradle.api.GradleException
import org.moe.common.exec.AbstractJarExec
import org.moe.common.exec.ExecRunnerBase
import org.moe.gradle.util.DeviceLauncherExec

/**
 * App launcher task for running apps on an iOS Device.
 */
public class DeviceAppLauncher extends AbstractAppLauncher {

    public static ArrayList<String> listUDIDs() {
        def list = parseExecList(new DeviceLauncherExec(), "Connected iOS Devices:")
        return list
    }

    @Override
    protected AbstractJarExec getExec() {
        return new DeviceLauncherExec()
    }

    @Override
    protected Collection<String> getTargetDevices() {
        return listUDIDs()
    }

    @Override
    protected void doRunOnTarget(String target) {
        def exec = getExec()
        ArrayList<String> args = exec.getArguments()
        args.add(getDeviceArg() + "=" + target)
        args.add("--app-path=" + getAppFile().getAbsolutePath())
        if (exec.getClass() == DeviceLauncherExec.class && getInstallOnly()) {
            args.add("--install-mode=installonly")
        }
        if (getDebug()) {
            args.add("--debug=" + getDebugPort())
        }
        if (!getEnableAndroidLog()) {
            args.add("--env=ANDROID_LOG_TAGS=*:s")
        }
        if (getLog() != null) {
            args.add("--output-file=" + getLog().getAbsolutePath())
        }

        def runner = exec.getRunner()

        try {
            runner.setListener(new ExecRunnerBase.ExecRunnerListener() {
                @Override
                public void stdout(String line) {
                    println line
                }

                @Override
                public void stderr(String line) {
                    println line
                }
            });

            if (runner.run(null) != 0) {
                if (installOnly) {
                    throw new GradleException("failed to install application")
                } else {
                    throw new GradleException("failed to install or launch application")
                }
            }
        } catch (GradleException e) {
            throw e
        } catch (Exception e) {
            throw new GradleException(e)
        }
    }
}
