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

import org.moe.common.exec.ExecRunnerBase
import org.moe.gradle.util.DeviceLauncherExec
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input

/**
 * Abstract app launcher.
 */
abstract class AbstractAppLauncher extends AbstractAppRunner {

    @Override
    protected boolean enablesMultipleTargets() {
        return installOnly
    }

    /*
	Task inputs
     */

    @Input
    boolean installOnly = false

    @Input
    boolean enableAndroidLog = true

    /*
	Task action
     */

    @Override
    protected void beforeAction() {
        project.logger.debug("|--- $name : ${getClass().getSimpleName()} ---|")
        project.logger.debug("|< installOnly: ${getInstallOnly()}")
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

        def runner = exec.getRunner()

        FileOutputStream fstream = null
        try {
            final FileOutputStream ostream = fstream = new FileOutputStream(getLog())
            final byte[] separator = System.getProperty("line.separator").getBytes()
            runner.setListener(new ExecRunnerBase.ExecRunnerListener() {
                @Override
                public void stdout(String line) {
                    try {
                        ostream.write(line.getBytes())
                        ostream.write(separator)
                    } catch (Exception e) {
                        println e.getMessage()
                    }

                    println line
                }

                @Override
                public void stderr(String line) {
                    try {
                        ostream.write(line.getBytes())
                        ostream.write(separator)
                    } catch (Exception e) {
                        println e.getMessage()
                    }

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
        } finally {
            if (fstream != null) {
                try {
                    fstream.flush()
                    fstream.close()
                } catch (Exception e) {
                    println e.getMessage()
                }
            }
        }
    }
}
