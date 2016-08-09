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

import org.moe.common.exec.AbstractExec
import org.moe.common.exec.AbstractJarExec
import org.moe.common.exec.ExecRunner
import org.moe.common.exec.IOutputListener
import org.gradle.api.tasks.*

/**
 * Abstract app runner.
 */
abstract class AbstractAppRunner extends BaseTask {

    protected static ArrayList<String> parseExecList(AbstractExec exec, String head) {
        if (exec == null || head == null) {
            throw new NullPointerException()
        }

        final ArrayList<String> list = new ArrayList<String>()
        exec.arguments.add("--list")
        final ExecRunner runner = exec.getRunner()
        runner.listener = new IOutputListener() {

            boolean inReadMode = false
            boolean completed = false

            @Override
            public void output(String line) {
                if (isCompleted()) {
                    return
                }

                if (head.equals(line)) {
                    setInReadMode(true)
                } else if (isInReadMode()) {
                    if (line.startsWith("- ")) {
                        list.add(line.substring(2))
                    } else {
                        setCompleted(true)
                    }
                }
            }
        }
        runner.run(null)
        return list
    }

    /**
     * Returns a new instance of the target exec.
     * @return new instance of the target exec
     */
    protected abstract AbstractJarExec getExec()

    /**
     * Returns the argument prefix used for device specification.
     * @return device specification argument prefix
     */
    protected String getDeviceArg() {
        return "--udid"
    }

    /**
     * Returns a list of the targeted devices.
     * @return list of the targeted devices
     */
    protected abstract Collection<String> getTargetDevices()

    protected boolean enablesMultipleTargets() {
        return true
    }

    /*
	Task inputs
     */

    // Disables incremental build
    @Input
    String __date = new Date().toString()

    @Input
    @Optional
    Collection<String> targets

    @InputDirectory
    File appFile

    @Input
    boolean debug = false

    @Input
    String debugPort = "5005"

    /*
    Task outputs
     */

    @OutputFile
    File log

    /*
	Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : ${getClass().getSimpleName()} ---|")
        project.logger.debug("|< targets: ${getTargets()}")
        project.logger.debug("|< appFile: ${getAppFile()}")
        project.logger.debug("|< debug: ${getDebug()}")
        project.logger.debug("|< debugPort: ${getDebugPort()}")
        project.logger.debug("|> log: ${getLog()}")

        beforeAction()

        securedLoggableAction(getLog()) {
            def ts = getTargets()
            if (ts == null || ts.size() == 0) {
                ts = getTargetDevices()
            }
            if (!enablesMultipleTargets() && ts.size() > 0) {
                ts = ts.asList().subList(0, 1)
            }
            ts.each {
                doRunOnTarget(it)
            }
        }

        afterAction()
    }

    protected void beforeAction() {
        // Do nothing
    }

    protected void afterAction() {
        // Do nothing
    }

    abstract protected void doRunOnTarget(String target);
}
