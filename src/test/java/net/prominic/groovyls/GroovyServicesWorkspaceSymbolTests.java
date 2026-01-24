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
package net.prominic.groovyls;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesWorkspaceSymbolTests {
	private static final String PATH_WORKSPACE = "./build/test_workspace/";

	private GroovyServices services;
	private Path workspaceRoot;

	@BeforeEach
	void setup() throws Exception {
		workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
		if (!Files.exists(workspaceRoot)) {
			Files.createDirectories(workspaceRoot);
		}

		services = new GroovyServices(new CompilationUnitFactory());
		services.setWorkspaceRoot(workspaceRoot);
		services.connect(new LanguageClient() {

			@Override
			public void telemetryEvent(Object object) {
			}

			@Override
			public CompletableFuture<MessageActionItem> showMessageRequest(
					org.eclipse.lsp4j.ShowMessageRequestParams requestParams) {
				return null;
			}

			@Override
			public void showMessage(MessageParams messageParams) {
			}

			@Override
			public void publishDiagnostics(org.eclipse.lsp4j.PublishDiagnosticsParams diagnostics) {
			}

			@Override
			public void logMessage(MessageParams message) {
			}
		});
	}

	@AfterEach
	void tearDown() {
		services = null;
		workspaceRoot = null;
	}

	@Test
	void testGspTemplatesAppearInWorkspaceSymbols() throws Exception {
		Path grailsApp = workspaceRoot.resolve("grails-app");
		Path confDir = grailsApp.resolve("conf");
		Files.createDirectories(confDir);
		Files.writeString(confDir.resolve("application.yml"), "grails:\n  profile: web\n");

		Path viewsDir = grailsApp.resolve("views").resolve("book");
		Files.createDirectories(viewsDir);
		Path gspFile = viewsDir.resolve("index.gsp");
		Files.writeString(gspFile, "<html><body>Index</body></html>");

		services.setWorkspaceRoot(workspaceRoot);
		services.didChangeWatchedFiles(new org.eclipse.lsp4j.DidChangeWatchedFilesParams(List.of()));

		WorkspaceSymbolParams params = new WorkspaceSymbolParams("index");
		Either<List<? extends org.eclipse.lsp4j.SymbolInformation>, List<? extends WorkspaceSymbol>> result = services
				.symbol(params).get();
		Assertions.assertTrue(result.isRight());
		List<? extends WorkspaceSymbol> symbols = result.getRight();
		Assertions.assertTrue(symbols.stream().anyMatch(symbol -> "index.gsp".equals(symbol.getName())));
	}
}
