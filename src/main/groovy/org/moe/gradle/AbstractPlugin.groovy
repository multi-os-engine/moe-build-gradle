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

import org.apache.commons.lang.StringUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Delete

abstract class AbstractPlugin {

    protected Project project

    protected JavaPlugin javaPlugin

    private Task lifecycleTask
    private Task cleanTask

    public Task getLifecycleTask() {
        return lifecycleTask
    }

    public Task getCleanTask() {
        return cleanTask
    }

    public Task getCleanTask(Task worker) {
        return project.getTasks().getByName(cleanName(worker.getName()))
    }

    protected String cleanName(String taskName) {
        return String.format("clean%s", StringUtils.capitalize(taskName))
    }

    public void apply(Project target) {
        this.project = target
        this.javaPlugin = project.plugins.apply(JavaPlugin.class)

        String lifecyleTaskName = getLifecycleTaskName()
        lifecycleTask = target.task(lifecyleTaskName)
        lifecycleTask.setGroup("MOE")
        cleanTask = target.task(cleanName(lifecyleTaskName))
        cleanTask.setGroup("MOE")
        onApply(target)
    }

    protected void addWorker(Task worker, boolean includeInClean = true) {
        lifecycleTask.dependsOn(worker)
        Delete cleanWorker = project.tasks.create(cleanName(worker.name), Delete.class)
        cleanWorker.delete(worker.getOutputs().getFiles())
        if (includeInClean) {
            cleanTask.dependsOn(cleanWorker)
        }
    }

    abstract protected void onApply(Project target);

    protected abstract String getLifecycleTaskName()
}
