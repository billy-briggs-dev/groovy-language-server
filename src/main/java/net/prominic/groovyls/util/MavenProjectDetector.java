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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class MavenProjectDetector {
    private static final String POM_FILE = "pom.xml";
    private static final int MAX_PROPERTY_DEPTH = 5;

    private MavenProjectDetector() {
    }

    public static MavenProjectInfo detect(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return null;
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path rootPom = root.resolve(POM_FILE);
        if (!Files.exists(rootPom)) {
            return null;
        }

        Set<Path> pomFiles = new LinkedHashSet<>();
        Set<Path> moduleDirectories = new LinkedHashSet<>();
        Set<String> repositories = new LinkedHashSet<>();
        Set<String> dependencies = new LinkedHashSet<>();

        collectPom(rootPom, pomFiles, moduleDirectories, repositories, dependencies, new LinkedHashSet<>());

        return new MavenProjectInfo(root, new ArrayList<>(pomFiles), new ArrayList<>(moduleDirectories),
                new ArrayList<>(repositories), new ArrayList<>(dependencies));
    }

    private static void collectPom(Path pom, Set<Path> pomFiles, Set<Path> moduleDirectories,
            Set<String> repositories, Set<String> dependencies, Set<Path> visited) {
        if (pom == null || !Files.exists(pom)) {
            return;
        }
        Path normalized = pom.toAbsolutePath().normalize();
        if (visited.contains(normalized)) {
            return;
        }
        visited.add(normalized);
        pomFiles.add(normalized);

        PomModel model = parsePom(normalized);
        if (model == null) {
            return;
        }

        repositories.addAll(model.repositories);
        dependencies.addAll(model.dependencies);

        for (String modulePath : model.modules) {
            Path moduleDir = normalized.getParent().resolve(modulePath).normalize();
            if (!Files.isDirectory(moduleDir)) {
                continue;
            }
            moduleDirectories.add(moduleDir);
            Path modulePom = moduleDir.resolve(POM_FILE);
            collectPom(modulePom, pomFiles, moduleDirectories, repositories, dependencies, visited);
        }
    }

    private static PomModel parsePom(Path pomPath) {
        Document doc = loadDocument(pomPath);
        if (doc == null) {
            return null;
        }
        Element project = doc.getDocumentElement();
        if (project == null) {
            return null;
        }

        PomModel parent = null;
        Element parentNode = getChild(project, "parent");
        if (parentNode != null) {
            Path parentPath = resolveParentPath(pomPath, parentNode);
            if (parentPath != null && Files.exists(parentPath)) {
                parent = parsePom(parentPath);
            }
        }

        Map<String, String> properties = new LinkedHashMap<>();
        if (parent != null) {
            properties.putAll(parent.properties);
        }
        properties.putAll(parseProperties(project));

        String groupId = textOrNull(getChild(project, "groupId"));
        String artifactId = textOrNull(getChild(project, "artifactId"));
        String version = textOrNull(getChild(project, "version"));
        String packaging = textOrNull(getChild(project, "packaging"));

        if (groupId == null && parent != null) {
            groupId = parent.groupId;
        }
        if (version == null && parent != null) {
            version = parent.version;
        }

        properties.put("project.groupId", groupId == null ? "" : groupId);
        properties.put("project.version", version == null ? "" : version);
        properties.put("project.artifactId", artifactId == null ? "" : artifactId);
        properties.put("pom.groupId", groupId == null ? "" : groupId);
        properties.put("pom.version", version == null ? "" : version);
        properties.put("pom.artifactId", artifactId == null ? "" : artifactId);

        List<String> modules = parseModules(project);
        List<String> repositories = parseRepositories(project, properties);
        Map<String, String> managedVersions = new LinkedHashMap<>();
        if (parent != null) {
            managedVersions.putAll(parent.managedVersions);
        }
        managedVersions.putAll(parseDependencyManagement(project, properties));
        List<String> dependencies = parseDependencies(project, properties, managedVersions);

        return new PomModel(groupId, artifactId, version, packaging, properties, modules, repositories, dependencies,
                managedVersions);
    }

    private static Document loadDocument(Path pomPath) {
        try (InputStream input = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(input);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path resolveParentPath(Path pomPath, Element parentNode) {
        String relative = textOrNull(getChild(parentNode, "relativePath"));
        if (relative == null || relative.isBlank()) {
            relative = "../pom.xml";
        }
        Path parentPath = pomPath.getParent().resolve(relative).normalize();
        if (Files.exists(parentPath)) {
            return parentPath;
        }
        return null;
    }

    private static Map<String, String> parseProperties(Element project) {
        Element props = getChild(project, "properties");
        if (props == null) {
            return Collections.emptyMap();
        }
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList nodes = props.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String key = node.getNodeName();
            String value = node.getTextContent();
            if (key != null && value != null) {
                properties.put(key, value.trim());
            }
        }
        return properties;
    }

    private static List<String> parseModules(Element project) {
        Element modules = getChild(project, "modules");
        if (modules == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        NodeList nodes = modules.getElementsByTagName("module");
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                results.add(value.trim());
            }
        }
        return results;
    }

    private static List<String> parseRepositories(Element project, Map<String, String> properties) {
        Element repositories = getChild(project, "repositories");
        if (repositories == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        NodeList nodes = repositories.getElementsByTagName("repository");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element repo = asElement(nodes.item(i));
            if (repo == null) {
                continue;
            }
            String url = resolvePlaceholders(textOrNull(getChild(repo, "url")), properties);
            if (url != null && !url.isBlank()) {
                results.add(url.trim());
            }
        }
        return results;
    }

    private static Map<String, String> parseDependencyManagement(Element project, Map<String, String> properties) {
        Element management = getChild(project, "dependencyManagement");
        if (management == null) {
            return Collections.emptyMap();
        }
        Element dependencies = getChild(management, "dependencies");
        if (dependencies == null) {
            return Collections.emptyMap();
        }
        Map<String, String> managed = new LinkedHashMap<>();
        NodeList nodes = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element dep = asElement(nodes.item(i));
            if (dep == null) {
                continue;
            }
            String groupId = resolvePlaceholders(textOrNull(getChild(dep, "groupId")), properties);
            String artifactId = resolvePlaceholders(textOrNull(getChild(dep, "artifactId")), properties);
            String version = resolvePlaceholders(textOrNull(getChild(dep, "version")), properties);
            if (groupId == null || artifactId == null || version == null) {
                continue;
            }
            managed.put(groupId.trim() + ":" + artifactId.trim(), version.trim());
        }
        return managed;
    }

    private static List<String> parseDependencies(Element project, Map<String, String> properties,
            Map<String, String> managedVersions) {
        Element dependencies = getChild(project, "dependencies");
        if (dependencies == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        NodeList nodes = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element dep = asElement(nodes.item(i));
            if (dep == null) {
                continue;
            }
            String scope = resolvePlaceholders(textOrNull(getChild(dep, "scope")), properties);
            if (scope != null && scope.equalsIgnoreCase("test")) {
                continue;
            }
            String groupId = resolvePlaceholders(textOrNull(getChild(dep, "groupId")), properties);
            String artifactId = resolvePlaceholders(textOrNull(getChild(dep, "artifactId")), properties);
            String version = resolvePlaceholders(textOrNull(getChild(dep, "version")), properties);
            String classifier = resolvePlaceholders(textOrNull(getChild(dep, "classifier")), properties);
            String type = resolvePlaceholders(textOrNull(getChild(dep, "type")), properties);
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (version == null || version.isBlank()) {
                String managed = managedVersions.get(groupId.trim() + ":" + artifactId.trim());
                if (managed != null) {
                    version = managed;
                }
            }
            if (version == null || version.isBlank()) {
                continue;
            }
            StringBuilder coordinate = new StringBuilder();
            coordinate.append(groupId.trim()).append(":").append(artifactId.trim()).append(":")
                    .append(version.trim());
            if (classifier != null && !classifier.isBlank()) {
                coordinate.append(":").append(classifier.trim());
            }
            if (type != null && !type.isBlank() && !"jar".equalsIgnoreCase(type.trim())) {
                coordinate.append("@").append(type.trim());
            }
            results.add(coordinate.toString());
        }
        return results;
    }

    private static String resolvePlaceholders(String value, Map<String, String> properties) {
        if (value == null) {
            return null;
        }
        String resolved = value;
        for (int depth = 0; depth < MAX_PROPERTY_DEPTH; depth++) {
            int start = resolved.indexOf("${");
            if (start < 0) {
                break;
            }
            int end = resolved.indexOf('}', start + 2);
            if (end < 0) {
                break;
            }
            String key = resolved.substring(start + 2, end);
            String replacement = properties.getOrDefault(key, "");
            resolved = resolved.substring(0, start) + replacement + resolved.substring(end + 1);
        }
        return resolved;
    }

    private static Element getChild(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getParentNode() == parent) {
                return asElement(node);
            }
        }
        return null;
    }

    private static Element asElement(Node node) {
        return node instanceof Element ? (Element) node : null;
    }

    private static String textOrNull(Element element) {
        if (element == null) {
            return null;
        }
        String value = element.getTextContent();
        return value == null ? null : value.trim();
    }

    private static final class PomModel {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String packaging;
        private final Map<String, String> properties;
        private final List<String> modules;
        private final List<String> repositories;
        private final List<String> dependencies;
        private final Map<String, String> managedVersions;

        private PomModel(String groupId, String artifactId, String version, String packaging,
                Map<String, String> properties, List<String> modules, List<String> repositories,
                List<String> dependencies, Map<String, String> managedVersions) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
            this.properties = properties == null ? Collections.emptyMap() : new LinkedHashMap<>(properties);
            this.modules = modules == null ? Collections.emptyList() : new ArrayList<>(modules);
            this.repositories = repositories == null ? Collections.emptyList() : new ArrayList<>(repositories);
            this.dependencies = dependencies == null ? Collections.emptyList() : new ArrayList<>(dependencies);
            this.managedVersions = managedVersions == null ? Collections.emptyMap()
                    : new LinkedHashMap<>(managedVersions);
        }
    }
}
