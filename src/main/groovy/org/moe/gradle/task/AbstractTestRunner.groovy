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

import org.moe.common.exec.IOutputListener
import org.moe.gradle.util.JUnitTestCollector
import org.gradle.api.GradleException
import org.gradle.api.tasks.OutputDirectory

/**
 * Abstract test runner.
 */
abstract class AbstractTestRunner extends AbstractAppRunner {

    /**
     * Number of failed tests
     */
    protected int numFailedTests = 0

    /**
     * Don't throw exception if the task failed.
     */
    private boolean dontThrowExOnFail = false

    /**
     * Set don't throw exception if the task failed.
     */
    public void skipThrowOnFail() {
        dontThrowExOnFail = true
    }

    /*
    Task outputs
     */

    @OutputDirectory
    File reportsDir

    /*
	Task action
     */

    @Override
    protected void beforeAction() {
        project.logger.debug("|--- $name : ${getClass().getSimpleName()} ---|")
        project.logger.debug("|> reportsDir: ${getReportsDir()}")

        // Clear results from other runs
        getReportsDir().deleteDir()
        getReportsDir().mkdir()
    }

    @Override
    protected void afterAction() {
        if (!dontThrowExOnFail && numFailedTests > 0) {
            throw new GradleException("Some JUnit tests failed, " +
                    "you can view the reports here: ${getReportsDir()}")
        }
    }

    @Override
    protected void doRunOnTarget(String target) {
        def exec = getExec()
        exec.arguments.add(getDeviceArg() + "=" + target)
        exec.arguments.add("--app-path=" + getAppFile().getAbsolutePath())
        if (getDebug()) {
            exec.arguments.add("--debug=" + getDebugPort())
        }
        def runner = exec.getRunner()
        JUnitTestCollector collector = new JUnitTestCollector()
        StringBuilder fullOutput = new StringBuilder()
        runner.listener = new IOutputListener() {
            @Override
            void output(String line) {
                collector.appendLine(line)
                fullOutput.append(line).append('\n')
            }
        }
        if (runner.run(null) != 0 && !collector.getHasEnded()) {
            ++numFailedTests
        } else {
            File output = new File(getReportsDir(), "${target}.xml")
            output.text = collector.getXMLReport()
            output = new File(getReportsDir(), "${target}.html")
            output.text = collector.getHTMLReport(target)
        }

        File output = new File(getReportsDir(), "${target}.output.txt")
        output.text = fullOutput

        numFailedTests += collector.getNumFailures() + collector.getNumErrors()
    }
}
