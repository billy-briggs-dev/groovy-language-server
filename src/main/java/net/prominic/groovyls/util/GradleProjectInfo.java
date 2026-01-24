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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class GradleProjectInfo {
    private final Path root;
    private final List<Path> buildFiles;
    private final List<Path> settingsFiles;
    private final List<Path> moduleDirectories;

    public GradleProjectInfo(Path root, List<Path> buildFiles, List<Path> settingsFiles,
            List<Path> moduleDirectories) {
        this.root = root;
        this.buildFiles = Collections.unmodifiableList(buildFiles);
        this.settingsFiles = Collections.unmodifiableList(settingsFiles);
        this.moduleDirectories = Collections.unmodifiableList(moduleDirectories);
    }

    public Path getRoot() {
        return root;
    }

    public List<Path> getBuildFiles() {
        return buildFiles;
    }

    public List<Path> getSettingsFiles() {
        return settingsFiles;
    }

    public List<Path> getModuleDirectories() {
        return moduleDirectories;
    }

    public boolean isMultiModule() {
        return !moduleDirectories.isEmpty();
    }
}
