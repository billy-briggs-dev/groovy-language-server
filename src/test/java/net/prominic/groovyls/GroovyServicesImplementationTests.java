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
package net.prominic.groovyls;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesImplementationTests {
	private static final String LANGUAGE_GROOVY = "groovy";
	private static final String PATH_WORKSPACE = "./build/test_workspace/";
	private static final String PATH_SRC = "./src/main/groovy";

	private GroovyServices services;
	private Path workspaceRoot;
	private Path srcRoot;

	@BeforeEach
	void setup() {
		workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
		srcRoot = workspaceRoot.resolve(PATH_SRC);
		if (!Files.exists(srcRoot)) {
			srcRoot.toFile().mkdirs();
		}

		services = new GroovyServices(new CompilationUnitFactory());
		services.setWorkspaceRoot(workspaceRoot);
		services.connect(new LanguageClient() {

			@Override
			public void telemetryEvent(Object object) {

			}

			@Override
			public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
				return null;
			}

			@Override
			public void showMessage(MessageParams messageParams) {

			}

			@Override
			public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {

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
		srcRoot = null;
	}

	@Test
	void testImplementationForInterface() throws Exception {
		Path filePath = srcRoot.resolve("Definitions.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package definitions\n");
		contentBuilder.append("interface MyInterface {\n");
		contentBuilder.append("  void myMethod()\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class MyImpl implements MyInterface {\n");
		contentBuilder.append("  void myMethod() {}\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class AnotherImpl implements MyInterface {\n");
		contentBuilder.append("  void myMethod() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		
		// Position on "MyInterface" in line 1 (0-indexed)
		Position position = new Position(1, 11);
		ImplementationParams params = new ImplementationParams(textDocument, position);
		
		Either<List<? extends Location>, List<? extends LocationLink>> result = services.implementation(params).get();
		
		Assertions.assertTrue(result.isLeft());
		List<? extends Location> locations = result.getLeft();
		Assertions.assertEquals(2, locations.size(), "Should find 2 implementations");
	}

	@Test
	void testImplementationForInterfaceMethod() throws Exception {
		Path filePath = srcRoot.resolve("MethodDefs.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package definitions\n");
		contentBuilder.append("interface MyInterface {\n");
		contentBuilder.append("  void myMethod()\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class MyImpl implements MyInterface {\n");
		contentBuilder.append("  void myMethod() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		
		// Position on "myMethod" in the interface (line 2, 0-indexed)
		Position position = new Position(2, 7);
		ImplementationParams params = new ImplementationParams(textDocument, position);
		
		Either<List<? extends Location>, List<? extends LocationLink>> result = services.implementation(params).get();
		
		Assertions.assertTrue(result.isLeft());
		List<? extends Location> locations = result.getLeft();
		Assertions.assertEquals(1, locations.size(), "Should find 1 method implementation");
	}

	@Test
	void testImplementationForAbstractClass() throws Exception {
		Path filePath = srcRoot.resolve("AbstractDefs.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package definitions\n");
		contentBuilder.append("abstract class AbstractBase {\n");
		contentBuilder.append("  abstract void abstractMethod()\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class ConcreteImpl extends AbstractBase {\n");
		contentBuilder.append("  void abstractMethod() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		
		// Position on "AbstractBase" in line 1 (0-indexed)
		Position position = new Position(1, 16);
		ImplementationParams params = new ImplementationParams(textDocument, position);
		
		Either<List<? extends Location>, List<? extends LocationLink>> result = services.implementation(params).get();
		
		Assertions.assertTrue(result.isLeft());
		List<? extends Location> locations = result.getLeft();
		Assertions.assertEquals(1, locations.size(), "Should find 1 concrete implementation");
	}

	@Test
	void testNoImplementationForConcreteClass() throws Exception {
		Path filePath = srcRoot.resolve("ConcreteDefs.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package definitions\n");
		contentBuilder.append("class ConcreteClass {\n");
		contentBuilder.append("  void myMethod() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		
		// Position on "ConcreteClass" in line 1 (0-indexed)
		Position position = new Position(1, 6);
		ImplementationParams params = new ImplementationParams(textDocument, position);
		
		Either<List<? extends Location>, List<? extends LocationLink>> result = services.implementation(params).get();
		
		Assertions.assertTrue(result.isLeft());
		List<? extends Location> locations = result.getLeft();
		Assertions.assertEquals(0, locations.size(), "Should find no implementations for concrete class");
	}
}
