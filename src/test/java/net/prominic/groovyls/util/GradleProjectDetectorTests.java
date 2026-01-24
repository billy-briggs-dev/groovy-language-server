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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GradleProjectDetectorTests {
    private Path workspaceRoot;

    @BeforeEach
    void setup() throws IOException {
        workspaceRoot = Files.createTempDirectory("groovyls-gradle");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (workspaceRoot != null && Files.exists(workspaceRoot)) {
            Files.walk(workspaceRoot).sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // ignore cleanup errors
                }
            });
        }
    }

    @Test
    void detectsSingleModuleProject() throws Exception {
        Path buildFile = workspaceRoot.resolve("build.gradle");
        Files.writeString(buildFile, "plugins { id 'groovy' }");

        GradleProjectInfo info = GradleProjectDetector.detect(workspaceRoot);
        Assertions.assertNotNull(info);
        Assertions.assertEquals(workspaceRoot.toAbsolutePath().normalize(), info.getRoot());
        Assertions.assertEquals(1, info.getBuildFiles().size());
        Assertions.assertTrue(info.getSettingsFiles().isEmpty());
        Assertions.assertTrue(info.getModuleDirectories().isEmpty());
    }

    @Test
    void detectsMultiModuleProjectFromSettings() throws Exception {
        Files.writeString(workspaceRoot.resolve("settings.gradle"), "include 'app', ':lib:core'\n");
        Files.writeString(workspaceRoot.resolve("build.gradle"), "plugins { id 'groovy' }");

        Path appDir = workspaceRoot.resolve("app");
        Path libCoreDir = workspaceRoot.resolve("lib").resolve("core");
        Files.createDirectories(appDir);
        Files.createDirectories(libCoreDir);
        Files.writeString(appDir.resolve("build.gradle"), "plugins { id 'groovy' }");
        Files.writeString(libCoreDir.resolve("build.gradle.kts"), "plugins { groovy }\n");

        GradleProjectInfo info = GradleProjectDetector.detect(workspaceRoot);
        Assertions.assertNotNull(info);
        Assertions.assertTrue(info.isMultiModule());
        Assertions.assertEquals(1, info.getSettingsFiles().size());
        Assertions.assertEquals(3, info.getBuildFiles().size());
        Assertions.assertEquals(2, info.getModuleDirectories().size());
    }
}
