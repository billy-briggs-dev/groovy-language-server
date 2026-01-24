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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradleProjectDetector {
    private static final String BUILD_GRADLE = "build.gradle";
    private static final String BUILD_GRADLE_KTS = "build.gradle.kts";
    private static final String SETTINGS_GRADLE = "settings.gradle";
    private static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";
    private static final Pattern INCLUDE_PATTERN = Pattern
            .compile("^\\s*include\\s*(?:\\((.*)\\)|(.+))", Pattern.CASE_INSENSITIVE);

    private GradleProjectDetector() {
    }

    public static GradleProjectInfo detect(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return null;
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();

        List<Path> settingsFiles = new ArrayList<>();
        addIfExists(settingsFiles, root.resolve(SETTINGS_GRADLE));
        addIfExists(settingsFiles, root.resolve(SETTINGS_GRADLE_KTS));

        List<Path> buildFiles = new ArrayList<>();
        addIfExists(buildFiles, root.resolve(BUILD_GRADLE));
        addIfExists(buildFiles, root.resolve(BUILD_GRADLE_KTS));

        if (settingsFiles.isEmpty() && buildFiles.isEmpty()) {
            return null;
        }

        Set<Path> moduleDirectories = new LinkedHashSet<>();
        for (Path settingsFile : settingsFiles) {
            Set<String> includes = parseIncludes(settingsFile);
            for (String modulePath : includes) {
                Path moduleDir = settingsFile.getParent().resolve(modulePath).normalize();
                if (Files.isDirectory(moduleDir)) {
                    moduleDirectories.add(moduleDir);
                    addIfExists(buildFiles, moduleDir.resolve(BUILD_GRADLE));
                    addIfExists(buildFiles, moduleDir.resolve(BUILD_GRADLE_KTS));
                }
            }
        }

        return new GradleProjectInfo(root, buildFiles, settingsFiles, new ArrayList<>(moduleDirectories));
    }

    private static void addIfExists(List<Path> list, Path path) {
        if (path != null && Files.exists(path)) {
            list.add(path);
        }
    }

    private static Set<String> parseIncludes(Path settingsFile) {
        Set<String> includes = new LinkedHashSet<>();
        String content;
        try {
            content = Files.readString(settingsFile);
        } catch (IOException e) {
            return includes;
        }
        String withoutBlockComments = content.replaceAll("(?s)/\\*.*?\\*/", "");
        String[] lines = withoutBlockComments.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.replaceAll("//.*$", "").replaceAll("#.*$", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String args = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (args == null) {
                continue;
            }
            String[] parts = args.split(",");
            for (String part : parts) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("project(")) {
                    continue;
                }
                if ((token.startsWith("\"") && token.endsWith("\""))
                        || (token.startsWith("'") && token.endsWith("'"))) {
                    token = token.substring(1, token.length() - 1);
                }
                token = token.trim();
                if (token.startsWith(":")) {
                    token = token.substring(1);
                }
                if (token.isEmpty()) {
                    continue;
                }
                includes.add(token.replace(':', File.separatorChar));
            }
        }
        return includes;
    }
}
