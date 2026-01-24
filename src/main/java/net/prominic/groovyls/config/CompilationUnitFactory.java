////////////////////////////////////////////////////////////////////////////////
// Copyright 2022 Prominic.NET, Inc.
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
package net.prominic.groovyls.config;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

import groovy.lang.GroovyClassLoader;
import net.prominic.groovyls.compiler.control.GroovyLSCompilationUnit;
import net.prominic.groovyls.compiler.control.io.StringReaderSourceWithURI;
import net.prominic.groovyls.util.FileContentsTracker;

public class CompilationUnitFactory implements ICompilationUnitFactory {
	private static final String FILE_EXTENSION_GROOVY = ".groovy";
	private static final List<String> DEFAULT_EXCLUDE_PATTERNS = Arrays.asList(
			"**/build/**",
			"**/target/**",
			"**/.gradle/**",
			"**/.git/**",
			"**/.idea/**",
			"**/out/**",
			"**/bin/**"
	);
	private static final List<Path> DEFAULT_SOURCE_ROOT_SUFFIXES = Arrays.asList(
			Paths.get("src", "main", "groovy"),
			Paths.get("src", "test", "groovy")
	);

	private GroovyLSCompilationUnit compilationUnit;
	private CompilerConfiguration config;
	private GroovyClassLoader classLoader;
	private List<String> additionalClasspathList;
	private boolean classpathRecursive;
	private List<String> excludePatterns = new ArrayList<>();
	private List<String> sourceRoots = new ArrayList<>();
	private List<PathMatcher> excludeMatchers = new ArrayList<>();

	public CompilationUnitFactory() {
		buildExcludeMatchers();
	}

	public List<String> getAdditionalClasspathList() {
		return additionalClasspathList;
	}

	public void setAdditionalClasspathList(List<String> additionalClasspathList) {
		this.additionalClasspathList = additionalClasspathList;
		invalidateCompilationUnit();
	}

	public void setClasspathRecursive(boolean classpathRecursive) {
		if (this.classpathRecursive == classpathRecursive) {
			return;
		}
		this.classpathRecursive = classpathRecursive;
		invalidateCompilationUnit();
	}

	public void setExcludePatterns(List<String> excludePatterns) {
		List<String> next = excludePatterns == null ? new ArrayList<>() : new ArrayList<>(excludePatterns);
		if (Objects.equals(this.excludePatterns, next)) {
			return;
		}
		this.excludePatterns = next;
		buildExcludeMatchers();
		invalidateCompilationUnit();
	}

	public void setSourceRoots(List<String> sourceRoots) {
		List<String> next = sourceRoots == null ? new ArrayList<>() : new ArrayList<>(sourceRoots);
		if (Objects.equals(this.sourceRoots, next)) {
			return;
		}
		this.sourceRoots = next;
		invalidateCompilationUnit();
	}

	public void invalidateCompilationUnit() {
		compilationUnit = null;
		config = null;
		classLoader = null;
	}

	public GroovyLSCompilationUnit create(Path workspaceRoot, FileContentsTracker fileContentsTracker) {
		if (config == null) {
			config = getConfiguration();
		}

		if (classLoader == null) {
			classLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader().getParent(), config, true);
		}

		Set<URI> changedUris = fileContentsTracker.getChangedURIs();
		if (compilationUnit == null) {
			compilationUnit = new GroovyLSCompilationUnit(config, null, classLoader);
			// we don't care about changed URIs if there's no compilation unit yet
			changedUris = null;
		} else {
			compilationUnit.setClassLoader(classLoader);
			final Set<URI> urisToRemove = changedUris;
			List<SourceUnit> sourcesToRemove = new ArrayList<>();
			compilationUnit.iterator().forEachRemaining(sourceUnit -> {
				URI uri = sourceUnit.getSource().getURI();
				if (urisToRemove.contains(uri)) {
					sourcesToRemove.add(sourceUnit);
				}
			});
			// if an URI has changed, we remove it from the compilation unit so
			// that a new version can be built from the updated source file
			compilationUnit.removeSources(sourcesToRemove);
		}

		if (workspaceRoot != null) {
			List<Path> roots = resolveSourceRoots(workspaceRoot);
			if (roots.isEmpty()) {
				addDirectoryToCompilationUnit(workspaceRoot, workspaceRoot, compilationUnit, fileContentsTracker,
						changedUris);
			} else {
				for (Path root : roots) {
					addDirectoryToCompilationUnit(workspaceRoot, root, compilationUnit, fileContentsTracker, changedUris);
				}
			}
		} else {
			final Set<URI> urisToAdd = changedUris;
			fileContentsTracker.getOpenURIs().forEach(uri -> {
				// if we're only tracking changes, skip all files that haven't
				// actually changed
				if (urisToAdd != null && !urisToAdd.contains(uri)) {
					return;
				}
				String contents = fileContentsTracker.getContents(uri);
				addOpenFileToCompilationUnit(uri, contents, compilationUnit);
			});
		}

		return compilationUnit;
	}

	protected CompilerConfiguration getConfiguration() {
		CompilerConfiguration config = new CompilerConfiguration();

		Map<String, Boolean> optimizationOptions = new HashMap<>();
		optimizationOptions.put(CompilerConfiguration.GROOVYDOC, true);
		config.setOptimizationOptions(optimizationOptions);

		List<String> classpathList = new ArrayList<>();
		getClasspathList(classpathList);
		config.setClasspathList(classpathList);

		return config;
	}

	protected void getClasspathList(List<String> result) {
		try {
			java.net.URL location = CompilationUnitFactory.class.getProtectionDomain().getCodeSource().getLocation();
			if (location != null) {
				Path path = Paths.get(location.toURI());
				String entry = path.toString();
				if (!result.contains(entry)) {
					result.add(entry);
				}
			}
		} catch (Exception e) {
			// ignore classpath discovery failures
		}
		if (additionalClasspathList == null) {
			return;
		}

		for (String entry : additionalClasspathList) {
			boolean mustBeDirectory = false;
			if (entry.endsWith("*")) {
				entry = entry.substring(0, entry.length() - 1);
				mustBeDirectory = true;
			}

			File file = new File(entry);
			if (!file.exists()) {
				continue;
			}
			if (file.isDirectory()) {
				collectClasspathJars(file.toPath(), result);
			} else if (!mustBeDirectory && file.isFile()) {
				if (file.getName().endsWith(".jar")) {
					result.add(entry);
				}
			}
		}
	}

	private void collectClasspathJars(Path directory, List<String> result) {
		if (directory == null || !Files.isDirectory(directory)) {
			return;
		}
		if (!classpathRecursive) {
			File[] children = directory.toFile().listFiles();
			if (children == null) {
				return;
			}
			for (File child : children) {
				if (child.isFile() && child.getName().endsWith(".jar")) {
					result.add(child.getPath());
				}
			}
			return;
		}
		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.getFileName() != null && file.getFileName().toString().endsWith(".jar")) {
						result.add(file.toString());
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			System.err.println("Failed to scan classpath directory: " + directory);
		}
	}

	private void buildExcludeMatchers() {
		List<String> patterns = new ArrayList<>(DEFAULT_EXCLUDE_PATTERNS);
		patterns.addAll(excludePatterns);
		List<PathMatcher> matchers = new ArrayList<>();
		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank()) {
				continue;
			}
			String normalized = pattern.replace("/", File.separator);
			matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalized));
		}
		this.excludeMatchers = matchers;
	}

	private boolean shouldExclude(Path path, Path workspaceRoot) {
		if (path == null || excludeMatchers == null || excludeMatchers.isEmpty() || workspaceRoot == null) {
			return false;
		}
		Path relative;
		try {
			relative = workspaceRoot.normalize().relativize(path.normalize());
		} catch (IllegalArgumentException e) {
			return false;
		}
		for (PathMatcher matcher : excludeMatchers) {
			if (matcher.matches(relative)) {
				return true;
			}
		}
		return false;
	}

	private boolean isDefaultSourceRoot(Path dir) {
		for (Path suffix : DEFAULT_SOURCE_ROOT_SUFFIXES) {
			if (dir.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private List<Path> resolveSourceRoots(Path workspaceRoot) {
		if (workspaceRoot == null) {
			return new ArrayList<>();
		}
		List<Path> roots = new ArrayList<>();
		if (sourceRoots != null && !sourceRoots.isEmpty()) {
			for (String rawRoot : sourceRoots) {
				if (rawRoot == null || rawRoot.isBlank()) {
					continue;
				}
				Path rootPath = Paths.get(rawRoot);
				if (!rootPath.isAbsolute()) {
					rootPath = workspaceRoot.resolve(rootPath);
				}
				if (Files.isDirectory(rootPath)) {
					roots.add(rootPath.normalize());
				}
			}
			return roots;
		}
		if (excludeMatchers.isEmpty()) {
			buildExcludeMatchers();
		}
		Set<Path> detected = new LinkedHashSet<>();
		try {
			Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (shouldExclude(dir, workspaceRoot)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					if (isDefaultSourceRoot(dir)) {
						detected.add(dir.normalize());
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			System.err.println("Failed to detect source roots: " + workspaceRoot);
		}
		roots.addAll(detected);
		return roots;
	}

	protected void addDirectoryToCompilationUnit(Path workspaceRoot, Path dirPath, GroovyLSCompilationUnit compilationUnit,
			FileContentsTracker fileContentsTracker, Set<URI> changedUris) {
		try {
			if (Files.exists(dirPath)) {
				Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						if (workspaceRoot != null && shouldExclude(dir, workspaceRoot)) {
							return FileVisitResult.SKIP_SUBTREE;
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
						if (!filePath.toString().endsWith(FILE_EXTENSION_GROOVY)) {
							return FileVisitResult.CONTINUE;
						}
						URI fileURI = filePath.toUri();
						if (!fileContentsTracker.isOpen(fileURI)) {
							File file = filePath.toFile();
							if (file.isFile()) {
								if (changedUris == null || changedUris.contains(fileURI)) {
									compilationUnit.addSource(file);
								}
							}
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}

		} catch (IOException e) {
			System.err.println("Failed to walk directory for source files: " + dirPath);
		}
		fileContentsTracker.getOpenURIs().forEach(uri -> {
			Path openPath = Paths.get(uri);
			if (!openPath.normalize().startsWith(dirPath.normalize())) {
				return;
			}
			if (changedUris != null && !changedUris.contains(uri)) {
				return;
			}
			String contents = fileContentsTracker.getContents(uri);
			addOpenFileToCompilationUnit(uri, contents, compilationUnit);
		});
	}

	protected void addOpenFileToCompilationUnit(URI uri, String contents, GroovyLSCompilationUnit compilationUnit) {
		Path filePath = Paths.get(uri);
		SourceUnit sourceUnit = new SourceUnit(filePath.toString(),
				new StringReaderSourceWithURI(contents, uri, compilationUnit.getConfiguration()),
				compilationUnit.getConfiguration(), compilationUnit.getClassLoader(),
				compilationUnit.getErrorCollector());
		compilationUnit.addSource(sourceUnit);
	}
}