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
import org.moe.common.simulator.Simulator
import org.moe.common.simulator.SimulatorManager
import org.moe.common.exec.AbstractJarExec
import org.moe.common.exec.ProcessHandler
import org.gradle.api.GradleException

/**
 * App launcher task for running tests on the iOS Simulator.
 */
public class SimulatorAppLauncher extends AbstractAppLauncher {

    public static ArrayList<String> listUDIDs() {
        List<Simulator> simulators = SimulatorManager.getSimulators();

        if (simulators == null || simulators.size() == 0) {
            throw new RuntimeException("Can not get list of valid simulators. Please install at least one.");
        }

        def list = new ArrayList<String>()
        simulators.each {
            list.add(it.udid())
        }
        return list
    }

    public static ArrayList<String> getListSimulators() {
        List<Simulator> simulators = SimulatorManager.getSimulators();

        if (simulators == null || simulators.size() == 0) {
            throw new RuntimeException("Can not get list of valid simulators. Please install at least one.");
        }

        def list = new ArrayList<String>()
        simulators.each {
            list.add(it.name() + ' (' + it.sdk() + ') - ' + it.udid())
        }
        return list
    }

    @Override
    protected AbstractJarExec getExec() {
        throw new UnsupportedOperationException()
    }

    @Override
    protected Collection<String> getTargetDevices() {
        return listUDIDs()
    }

    @Override
    protected void doRunOnTarget(String target) {
        List<String> args = new ArrayList<String>()

        if (getDebug()) {
            args.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=" + getDebugPort())
        }

        Process process;
        SimulatorManager manager = new SimulatorManager()
        String errorMsg = 'failed to launch application on simulator'
        FileOutputStream fstream = null
        try {
            process = manager.installAndLaunchApp(target, getAppFile().getAbsolutePath(), args)

            if (process == null) {
                throw new GradleException(errorMsg)
            } else {
                def processHandler = new ProcessHandler(process)
                final FileOutputStream ostream = fstream = new FileOutputStream(getLog())
                final byte[] separator = System.getProperty("line.separator").getBytes()

                processHandler.setListener(new ExecRunnerBase.ExecRunnerListener() {
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

                if (processHandler.run(null) != 0) {
                    throw new GradleException(errorMsg)
                }
            }
        }
        catch (GradleException e) {
            throw e
        } catch (Exception e) {
            throw new GradleException(errorMsg, e)
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
