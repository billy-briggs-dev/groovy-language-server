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
import java.util.List;

public final class MavenDependencyResolver {
    private static final String DEFAULT_REPO = "https://repo1.maven.org/maven2";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
