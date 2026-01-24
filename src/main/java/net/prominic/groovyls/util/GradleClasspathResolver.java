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
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaCompilerOutput;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class GradleClasspathResolver {
    private static final Path CACHE_PATH = Paths.get(System.getProperty("user.home"), ".groovyls", "cache",
            "gradle-classpath.json");
    private static final Object CACHE_LOCK = new Object();
    private static final Gson GSON = new Gson();
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    static {
        loadCache();
    }

    private GradleClasspathResolver() {
    }

    public static List<String> resolve(GradleProjectInfo projectInfo) {
        if (projectInfo == null || projectInfo.getBuildFiles().isEmpty()) {
            return Collections.emptyList();
        }

        String key = projectInfo.getRoot().toAbsolutePath().normalize().toString();
        String signature = buildSignature(projectInfo);
        CacheEntry cached = CACHE.get(key);
        if (cached != null && signature.equals(cached.signature)) {
            return new ArrayList<>(cached.classpath);
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

            List<String> resolved = new ArrayList<>(classpathEntries);
            CACHE.put(key, new CacheEntry(signature, resolved));
            saveCache();
            return resolved;
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

    private static String buildSignature(GradleProjectInfo projectInfo) {
        List<Path> files = new ArrayList<>();
        files.addAll(projectInfo.getBuildFiles());
        files.addAll(projectInfo.getSettingsFiles());
        files.sort(Comparator.comparing(Path::toString));
        StringBuilder signature = new StringBuilder();
        for (Path file : files) {
            signature.append(file.toAbsolutePath().normalize());
            signature.append(":");
            try {
                signature.append(Files.getLastModifiedTime(file).toMillis());
            } catch (IOException e) {
                signature.append("0");
            }
            signature.append("|");
        }
        return signature.toString();
    }

    private static void loadCache() {
        synchronized (CACHE_LOCK) {
            if (!Files.exists(CACHE_PATH)) {
                return;
            }
            try {
                String json = Files.readString(CACHE_PATH);
                if (json == null || json.isBlank()) {
                    return;
                }
                Type type = new TypeToken<Map<String, CacheEntry>>() {
                }.getType();
                Map<String, CacheEntry> data = GSON.fromJson(json, type);
                if (data != null) {
                    CACHE.putAll(data);
                }
            } catch (Exception e) {
                // ignore cache load failures
            }
        }
    }

    private static void saveCache() {
        synchronized (CACHE_LOCK) {
            try {
                Path parent = CACHE_PATH.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                String json = GSON.toJson(new HashMap<>(CACHE));
                Files.writeString(CACHE_PATH, json);
            } catch (Exception e) {
                // ignore cache save failures
            }
        }
    }

    private static final class CacheEntry {
        private String signature;
        private List<String> classpath;

        private CacheEntry() {
        }

        private CacheEntry(String signature, List<String> classpath) {
            this.signature = signature;
            this.classpath = classpath == null ? Collections.emptyList() : new ArrayList<>(classpath);
        }
    }
}
