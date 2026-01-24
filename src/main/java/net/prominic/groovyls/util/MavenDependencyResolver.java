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
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class MavenDependencyResolver {
    private static final String DEFAULT_REPO = "https://repo1.maven.org/maven2";
    private static final Path CACHE_PATH = Paths.get(System.getProperty("user.home"), ".groovyls", "cache",
            "maven-classpath.json");
    private static final Object CACHE_LOCK = new Object();
    private static final Gson GSON = new Gson();
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static {
        loadCache();
    }

    private MavenDependencyResolver() {
    }

    public static List<String> resolve(List<String> dependencies, List<String> repositories) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> repoList = new ArrayList<>();
        if (repositories != null && !repositories.isEmpty()) {
            repoList.addAll(repositories);
        } else {
            repoList.add(DEFAULT_REPO);
        }

        String cacheKey = buildCacheKey(dependencies, repoList);
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached.classpath);
        }

        Path localRepo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        List<String> resolved = new ArrayList<>();

        for (String dependency : dependencies) {
            MavenCoordinate coordinate = MavenCoordinate.parse(dependency);
            if (coordinate == null) {
                continue;
            }
            Path artifactPath = coordinate.toLocalPath(localRepo);
            if (!Files.exists(artifactPath)) {
                boolean downloaded = downloadArtifact(repoList, coordinate, artifactPath);
                if (!downloaded) {
                    continue;
                }
            }
            if (Files.exists(artifactPath)) {
                resolved.add(artifactPath.toString());
            }
        }
        CACHE.put(cacheKey, new CacheEntry(resolved));
        saveCache();
        return resolved;
    }

    private static boolean downloadArtifact(List<String> repositories, MavenCoordinate coordinate, Path destination) {
        Path parent = destination.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                return false;
            }
        }
        for (String rawRepo : repositories) {
            if (rawRepo == null || rawRepo.isBlank()) {
                continue;
            }
            String repo = rawRepo.endsWith("/") ? rawRepo.substring(0, rawRepo.length() - 1) : rawRepo;
            String url = repo + "/" + coordinate.toRepositoryPath();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return true;
                }
            } catch (Exception e) {
                // try next repository
            }
        }
        return false;
    }

    private static String buildCacheKey(List<String> dependencies, List<String> repositories) {
        String deps = String.join("|", dependencies == null ? Collections.emptyList() : dependencies);
        String repos = String.join("|", repositories == null ? Collections.emptyList() : repositories);
        return deps + "::" + repos;
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
        private List<String> classpath;

        private CacheEntry() {
        }

        private CacheEntry(List<String> classpath) {
            this.classpath = classpath == null ? Collections.emptyList() : new ArrayList<>(classpath);
        }
    }

    private static final class MavenCoordinate {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;
        private final String extension;

        private MavenCoordinate(String groupId, String artifactId, String version, String classifier,
                String extension) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
            this.extension = extension;
        }

        static MavenCoordinate parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String value = raw.trim();
            String extension = "jar";
            if (value.contains("@")) {
                String[] parts = value.split("@", 2);
                value = parts[0];
                if (parts.length > 1 && !parts[1].isBlank()) {
                    extension = parts[1].trim();
                }
            }
            String[] parts = value.split(":");
            if (parts.length < 3) {
                return null;
            }
            String groupId = parts[0].trim();
            String artifactId = parts[1].trim();
            String version = parts[2].trim();
            String classifier = parts.length > 3 ? parts[3].trim() : null;
            if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                return null;
            }
            return new MavenCoordinate(groupId, artifactId, version, classifier, extension);
        }

        Path toLocalPath(Path localRepo) {
            return localRepo.resolve(toRepositoryPath());
        }

        String toRepositoryPath() {
            String groupPath = groupId.replace('.', '/');
            String base = artifactId + "-" + version;
            if (classifier != null && !classifier.isBlank()) {
                base += "-" + classifier;
            }
            return groupPath + "/" + artifactId + "/" + version + "/" + base + "." + extension;
        }
    }
}
