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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GrailsProjectDetector {
    private static final String GRAILS_APP = "grails-app";
    private static final String CONF = "conf";
    private static final String APPLICATION_YML = "application.yml";
    private static final String APPLICATION_GROOVY = "application.groovy";

    private static final String[] GRAILS_SOURCE_DIRS = new String[] {
            "controllers",
            "domain",
            "services",
            "taglib",
            "jobs",
            "utils",
            "init"
    };

    private GrailsProjectDetector() {
    }

    public static GrailsProjectInfo detect(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return null;
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path grailsApp = root.resolve(GRAILS_APP);
        if (!Files.isDirectory(grailsApp)) {
            return null;
        }
        Path confDir = grailsApp.resolve(CONF);
        if (!Files.exists(confDir.resolve(APPLICATION_YML)) && !Files.exists(confDir.resolve(APPLICATION_GROOVY))) {
            return null;
        }

        Set<Path> roots = new LinkedHashSet<>();
        for (String dir : GRAILS_SOURCE_DIRS) {
            Path candidate = grailsApp.resolve(dir);
            if (Files.isDirectory(candidate)) {
                roots.add(candidate.normalize());
            }
        }

        Path srcMainGroovy = root.resolve("src").resolve("main").resolve("groovy");
        if (Files.isDirectory(srcMainGroovy)) {
            roots.add(srcMainGroovy.normalize());
        }
        Path srcTestGroovy = root.resolve("src").resolve("test").resolve("groovy");
        if (Files.isDirectory(srcTestGroovy)) {
            roots.add(srcTestGroovy.normalize());
        }
        Path srcIntegrationGroovy = root.resolve("src").resolve("integration-test").resolve("groovy");
        if (Files.isDirectory(srcIntegrationGroovy)) {
            roots.add(srcIntegrationGroovy.normalize());
        }

        return new GrailsProjectInfo(root, new ArrayList<>(roots));
    }
}
