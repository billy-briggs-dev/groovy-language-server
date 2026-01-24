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
import java.util.stream.Collectors;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaCompilerOutput;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
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
        return resolve(projectInfo, Collections.emptyList(), false);
    }

    public static List<String> resolve(GradleProjectInfo projectInfo, List<String> scopes,
            boolean includeBuildscript) {
        if (projectInfo == null || projectInfo.getBuildFiles().isEmpty()) {
            return Collections.emptyList();
        }

        String key = projectInfo.getRoot().toAbsolutePath().normalize().toString();
        String signature = buildSignature(projectInfo, scopes, includeBuildscript);
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
            Set<String> allowedScopes = normalizeScopes(scopes);

            Map<String, IdeaModule> modulesByKey = ideaProject.getModules().stream().collect(Collectors.toMap(
                    GradleClasspathResolver::moduleKey,
                    module -> module,
                    (first, second) -> first));

            Set<IdeaModule> visited = Collections.newSetFromMap(new ConcurrentHashMap<>());

            for (IdeaModule module : ideaProject.getModules()) {
                collectModuleClasspath(module, classpathEntries, allowedScopes, modulesByKey, visited);
            }

            if (includeBuildscript) {
                addBuildscriptOutputs(projectInfo, classpathEntries);
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
        return buildSignature(projectInfo, Collections.emptyList(), false);
    }

    private static String buildSignature(GradleProjectInfo projectInfo, List<String> scopes,
            boolean includeBuildscript) {
        List<Path> files = new ArrayList<>();
        files.addAll(projectInfo.getBuildFiles());
        files.addAll(projectInfo.getSettingsFiles());
        files.sort(Comparator.comparing(Path::toString));
        StringBuilder signature = new StringBuilder();
        signature.append("scopes=").append(String.join(",", normalizeScopes(scopes))).append(";");
        signature.append("buildscript=").append(includeBuildscript).append("|");
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

    private static void collectModuleClasspath(IdeaModule module, Set<String> classpathEntries,
            Set<String> allowedScopes, Map<String, IdeaModule> modulesByKey, Set<IdeaModule> visited) {
        if (module == null || visited.contains(module)) {
            return;
        }
        visited.add(module);

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
            if (!isScopeAllowed(dependency, allowedScopes)) {
                continue;
            }
            if (dependency instanceof IdeaSingleEntryLibraryDependency) {
                File file = ((IdeaSingleEntryLibraryDependency) dependency).getFile();
                if (file != null && file.exists()) {
                    classpathEntries.add(file.getAbsolutePath());
                }
            } else if (dependency instanceof IdeaModuleDependency) {
                IdeaModuleDependency moduleDependency = (IdeaModuleDependency) dependency;
                IdeaModule target = resolveModule(moduleDependency.getTargetModuleName(), modulesByKey);
                collectModuleClasspath(target, classpathEntries, allowedScopes, modulesByKey, visited);
            }
        }
    }

    private static boolean isScopeAllowed(IdeaDependency dependency, Set<String> allowedScopes) {
        if (allowedScopes == null || allowedScopes.isEmpty() || dependency == null) {
            return true;
        }
        IdeaDependencyScope scope = dependency.getScope();
        if (scope == null || scope.getScope() == null) {
            return true;
        }
        return allowedScopes.contains(scope.getScope().toUpperCase());
    }

    private static Set<String> normalizeScopes(List<String> scopes) {
        if (scopes == null) {
            return Collections.emptySet();
        }
        return scopes.stream().filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> scope.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String moduleKey(IdeaModule module) {
        if (module == null || module.getGradleProject() == null) {
            return "";
        }
        String path = module.getGradleProject().getPath();
        return path == null ? "" : path;
    }

    private static IdeaModule resolveModule(String targetName, Map<String, IdeaModule> modulesByKey) {
        if (targetName == null || modulesByKey == null || modulesByKey.isEmpty()) {
            return null;
        }
        IdeaModule direct = modulesByKey.get(targetName);
        if (direct != null) {
            return direct;
        }
        String normalized = targetName.startsWith(":") ? targetName : ":" + targetName;
        IdeaModule normalizedMatch = modulesByKey.get(normalized);
        if (normalizedMatch != null) {
            return normalizedMatch;
        }
        String leaf = targetName.contains(":") ? targetName.substring(targetName.lastIndexOf(':') + 1)
                : targetName;
        for (Map.Entry<String, IdeaModule> entry : modulesByKey.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String keyLeaf = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
            if (leaf.equals(keyLeaf)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void addBuildscriptOutputs(GradleProjectInfo projectInfo, Set<String> classpathEntries) {
        if (projectInfo == null || projectInfo.getRoot() == null) {
            return;
        }
        Path buildSrc = projectInfo.getRoot().resolve("buildSrc");
        if (!Files.isDirectory(buildSrc)) {
            return;
        }
        addIfExists(classpathEntries, buildSrc.resolve("build").resolve("classes").resolve("java").resolve("main"));
        addIfExists(classpathEntries, buildSrc.resolve("build").resolve("classes").resolve("groovy").resolve("main"));
        addIfExists(classpathEntries, buildSrc.resolve("build").resolve("classes").resolve("kotlin").resolve("main"));
        addIfExists(classpathEntries, buildSrc.resolve("build").resolve("resources").resolve("main"));
    }

    private static void addIfExists(Set<String> classpathEntries, Path path) {
        if (path == null) {
            return;
        }
        File file = path.toFile();
        if (file.exists()) {
            classpathEntries.add(file.getAbsolutePath());
        }
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
