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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GrailsProjectDetectorTests {
    @Test
    void detectReturnsNullWithoutGrailsMarkers() throws Exception {
        Path tempDir = Files.createTempDirectory("groovyls-grails");
        GrailsProjectInfo info = GrailsProjectDetector.detect(tempDir);
        Assertions.assertNull(info);
    }

    @Test
    void detectBuildsSourceRootsWhenGrailsAppPresent() throws Exception {
        Path tempDir = Files.createTempDirectory("groovyls-grails");
        Path grailsApp = tempDir.resolve("grails-app");
        Path conf = grailsApp.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("application.yml"), "grails:\n  profile: web\n");

        Path domain = grailsApp.resolve("domain");
        Path controllers = grailsApp.resolve("controllers");
        Files.createDirectories(domain);
        Files.createDirectories(controllers);

        GrailsProjectInfo info = GrailsProjectDetector.detect(tempDir);
        Assertions.assertNotNull(info);
        Assertions.assertEquals(tempDir.toAbsolutePath().normalize(), info.getRoot());
        Assertions.assertTrue(info.getSourceRoots().contains(domain));
        Assertions.assertTrue(info.getSourceRoots().contains(controllers));
    }
}
