/*
 * Copyright (c) 2017 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liulishuo.okcheck

import com.liulishuo.okcheck.util.BuildConfig
import com.liulishuo.okcheck.util.ChangeFile
import com.liulishuo.okcheck.util.Util
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class OkCheckTask extends DefaultTask {
    @Input
    List<String> changedModuleList

    @Input
    boolean isMock

    @TaskAction
    void setupOkcheck() {
        if (project == project.rootProject) {
            Util.printLog("Finish root okcheck task!")
        } else if (!isMock) {
            Util.printLog("Finish ${project.name} okcheck task!")
            BuildConfig.addToPassedModuleFile(project)

            if (BuildConfig.isAllModulePassed(project, changedModuleList)) {
                ChangeFile changeFile = new ChangeFile(project.rootProject)
                changeFile.refreshLastExecCommitId()
                Util.printLog("All check is passed and refreshed the commit to current one!")
                String maintainInfo = changeFile.maintain().trim()
                if (maintainInfo.length() > 0) Util.printLog(maintainInfo)
            }
        }

    }

    static def addValidTask(Project project, List<String> moduleList, OkCheckExtension extension) {
        Util.addTaskWithVariants(project) { flavor, buildType, firstFlavor ->
            addValidTask(project, moduleList, extension, "${flavor.capitalize()}", "${buildType.capitalize()}", "$firstFlavor")
        }
    }

    static def addMockTask(Project project) {
        Util.addTaskWithVariants(project) { flavor, buildType, firstFlavor ->
            addMockTask(project, "${flavor.capitalize()}", "${buildType.capitalize()}")
        }
    }

    static
    def addValidTask(Project project, List<String> moduleList, OkCheckExtension extension, String flavor, String buildType, String firstFlavor) {
        String taskName = OkCheckPlugin.TASK_NAME + "$flavor$buildType"
        Set<String> dependsTaskNames = new HashSet<>()
        if (extension.lint.enabled) {
            dependsTaskNames.add(Util.getBuildInTaskName(project.name, taskName, "okLint", flavor, buildType, firstFlavor))
        }
        if (extension.unitTest.enabled) {
            // unit test only have test, test${buildType}UnitTest and test$flavor${buildType}UnitTest
            if (buildType.isEmpty() && flavor.isEmpty()) {
                dependsTaskNames.add("test")
            } else {
                dependsTaskNames.add(Util.getBuildInTaskName(project.name, taskName, "test", flavor, buildType, firstFlavor, "UnitTest"))
            }
        }
        if (extension.coverageReport.enabled) dependsTaskNames.add(OkCoverageReport.getTaskName(flavor, buildType))
        if (extension.checkStyle.enabled) dependsTaskNames.add(OkCheckStyleTask.NAME)
        if (extension.pmd.enabled) dependsTaskNames.add(OkPmdTask.NAME)
        if (extension.findbugs.enabled) dependsTaskNames.add("${OkFindbugsTask.NAME}$flavor$buildType")
        if (extension.ktlint.enabled) dependsTaskNames.add(OkKtlintTask.NAME)

        project.task(taskName, type: OkCheckTask, overwrite: true) {
//            inputs.files(Util.getAllInputs(project))
//            outputs.dir(project.buildDir)

            dependsOn dependsTaskNames
            setGroup("verification")
            if (flavor.length() <= 0 && buildType.length() <= 0) {
                setDescription("Run check only for changed files for all variants")
            } else {
                setDescription("Run check only for changed files for $flavor$buildType build.")
            }

            changedModuleList = moduleList
            isMock = false

            File unitTestReportDir = extension.getUnitTest().reportDir
            if (!unitTestReportDir.getAbsolutePath().startsWith(project.buildDir.getAbsolutePath()))
                doLast {
                    if (extension.unitTest.enabled) moveUnitTestReport(project, unitTestReportDir)
                }
        }
    }

    static def moveUnitTestReport(Project project, File targetDir) {
        File originDir = new File(project.buildDir, "reports/tests")
        if (originDir.exists()) {
            if (targetDir.exists()) FileUtils.forceDelete(targetDir)
            if (!targetDir.getParentFile().exists()) targetDir.getParentFile().mkdirs()
            FileUtils.moveDirectory(originDir, targetDir)
            Util.printLog("move ${originDir.path} to ${targetDir.path}.")
        }
    }

    static def addMockTask(Project project, String flavor, String buildType) {
        project.task(OkCheckPlugin.TASK_NAME + "$flavor$buildType", type: OkCheckTask, overwrite: true) {
            setGroup("verification")
            if (flavor.length() <= 0 && buildType.length() <= 0) {
                setDescription("Run check only for changed files for all variants")
            } else {
                setDescription("Run check only for changed files for $flavor$buildType build.")
            }
            changedModuleList = new ArrayList<>()
            isMock = true
        }
    }
}
