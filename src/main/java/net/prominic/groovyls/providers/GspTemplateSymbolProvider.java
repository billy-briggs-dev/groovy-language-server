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
package net.prominic.groovyls.providers;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;

import net.prominic.groovyls.util.GrailsProjectInfo;

public class GspTemplateSymbolProvider {
	private static final String GRAILS_APP = "grails-app";
	private static final String VIEWS = "views";
	private static final String EXTENSION = ".gsp";

	private final Path workspaceRoot;
	private final GrailsProjectInfo grailsProjectInfo;

	public GspTemplateSymbolProvider(Path workspaceRoot, GrailsProjectInfo grailsProjectInfo) {
		this.workspaceRoot = workspaceRoot;
		this.grailsProjectInfo = grailsProjectInfo;
	}

	public List<WorkspaceSymbol> provideWorkspaceSymbols(String query) {
		if (workspaceRoot == null || grailsProjectInfo == null) {
			return Collections.emptyList();
		}
		Path viewsRoot = workspaceRoot.resolve(GRAILS_APP).resolve(VIEWS);
		if (!Files.isDirectory(viewsRoot)) {
			return Collections.emptyList();
		}
		String lowerQuery = query == null ? "" : query.toLowerCase();
		List<WorkspaceSymbol> results = new ArrayList<>();
		try {
			Files.walkFileTree(viewsRoot, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file == null || !file.toString().endsWith(EXTENSION)) {
						return FileVisitResult.CONTINUE;
					}
					String fileName = file.getFileName().toString();
					String relative = viewsRoot.relativize(file).toString();
					String matchTarget = (fileName + " " + relative).toLowerCase();
					if (!lowerQuery.isBlank() && !matchTarget.contains(lowerQuery)) {
						return FileVisitResult.CONTINUE;
					}
					WorkspaceSymbol symbol = new WorkspaceSymbol();
					symbol.setName(fileName);
					symbol.setKind(SymbolKind.File);
					symbol.setContainerName(viewsRoot.relativize(file.getParent()).toString());
					Range range = new Range(new Position(0, 0), new Position(0, 0));
					Location location = new Location(file.toUri().toString(), range);
					symbol.setLocation(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(location));
					results.add(symbol);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			return Collections.emptyList();
		}
		return results;
	}
}
