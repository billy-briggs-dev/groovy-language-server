////////////////////////////////////////////////////////////////////////////////
// Copyright 2026 Prominic.NET, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License
//
// Author: Prominic.NET, Inc.
// No warranty of merchantability or fitness of any kind.
// Use this software at your own risk.
////////////////////////////////////////////////////////////////////////////////
package net.prominic.groovyls.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaCompilerOutput;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

public final class GradleClasspathResolver {
    private GradleClasspathResolver() {
    }

    public static List<String> resolve(GradleProjectInfo projectInfo) {
        if (projectInfo == null || projectInfo.getBuildFiles().isEmpty()) {
            return Collections.emptyList();
        }

        ProjectConnection connection = null;
        try {
            connection = GradleConnector.newConnector()
                    .forProjectDirectory(projectInfo.getRoot().toFile())
                    .connect();
            IdeaProject ideaProject = connection.getModel(IdeaProject.class);
            Set<String> classpathEntries = new LinkedHashSet<>();

            for (IdeaModule module : ideaProject.getModules()) {
                IdeaCompilerOutput output = module.getCompilerOutput();
                if (output != null) {
                    File mainOutput = output.getOutputDir();
                    if (mainOutput != null && mainOutput.exists()) {
                        classpathEntries.add(mainOutput.getAbsolutePath());
                    }
                    File testOutput = output.getTestOutputDir();
                    if (testOutput != null && testOutput.exists()) {
                        classpathEntries.add(testOutput.getAbsolutePath());
                    }
                }

                for (IdeaDependency dependency : module.getDependencies()) {
                    if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                        File file = ((IdeaSingleEntryLibraryDependency) dependency).getFile();
                        if (file != null && file.exists()) {
                            classpathEntries.add(file.getAbsolutePath());
                        }
                    }
                }
            }

            return new ArrayList<>(classpathEntries);
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    public static List<String> mergeClasspath(List<String> userClasspath, List<String> gradleClasspath) {
        Set<String> merged = new LinkedHashSet<>();
        if (userClasspath != null) {
            merged.addAll(userClasspath);
        }
        if (gradleClasspath != null) {
            merged.addAll(gradleClasspath);
        }
        return new ArrayList<>(merged);
    }
}
