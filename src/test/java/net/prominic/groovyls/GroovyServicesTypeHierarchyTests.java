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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesTypeHierarchyTests {
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
	void testPrepareTypeHierarchy() throws Exception {
		Path filePath = srcRoot.resolve("Hierarchy.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package hierarchy\n");
		contentBuilder.append("class BaseClass {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class DerivedClass extends BaseClass {\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 6); // On "BaseClass"
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams(textDocument, position);
		
		List<TypeHierarchyItem> result = services.prepareTypeHierarchy(params).get();
		
		Assertions.assertNotNull(result);
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("hierarchy.BaseClass", result.get(0).getName());
	}

	@Test
	void testTypeHierarchySupertypes() throws Exception {
		Path filePath = srcRoot.resolve("SuperHierarchy.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package hierarchy\n");
		contentBuilder.append("interface MyInterface {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class BaseClass implements MyInterface {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class DerivedClass extends BaseClass {\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(5, 6); // On "DerivedClass"
		TypeHierarchyPrepareParams prepareParams = new TypeHierarchyPrepareParams(textDocument, position);
		
		List<TypeHierarchyItem> prepareResult = services.prepareTypeHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		TypeHierarchyItem item = prepareResult.get(0);
		TypeHierarchySupertypesParams supertypesParams = new TypeHierarchySupertypesParams(item);
		List<TypeHierarchyItem> supertypes = services.typeHierarchySupertypes(supertypesParams).get();
		
		Assertions.assertNotNull(supertypes);
		Assertions.assertEquals(1, supertypes.size());
		Assertions.assertEquals("hierarchy.BaseClass", supertypes.get(0).getName());
	}

	@Test
	void testTypeHierarchySubtypes() throws Exception {
		Path filePath = srcRoot.resolve("SubHierarchy.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package hierarchy\n");
		contentBuilder.append("class BaseClass {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class DerivedClass1 extends BaseClass {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class DerivedClass2 extends BaseClass {\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 6); // On "BaseClass"
		TypeHierarchyPrepareParams prepareParams = new TypeHierarchyPrepareParams(textDocument, position);
		
		List<TypeHierarchyItem> prepareResult = services.prepareTypeHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		TypeHierarchyItem item = prepareResult.get(0);
		TypeHierarchySubtypesParams subtypesParams = new TypeHierarchySubtypesParams(item);
		List<TypeHierarchyItem> subtypes = services.typeHierarchySubtypes(subtypesParams).get();
		
		Assertions.assertNotNull(subtypes);
		Assertions.assertEquals(2, subtypes.size());
	}

	@Test
	void testTypeHierarchyForInterface() throws Exception {
		Path filePath = srcRoot.resolve("InterfaceHierarchy.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package hierarchy\n");
		contentBuilder.append("interface MyInterface {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class Implementation1 implements MyInterface {\n");
		contentBuilder.append("}\n");
		contentBuilder.append("class Implementation2 implements MyInterface {\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10); // On "MyInterface"
		TypeHierarchyPrepareParams prepareParams = new TypeHierarchyPrepareParams(textDocument, position);
		
		List<TypeHierarchyItem> prepareResult = services.prepareTypeHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		TypeHierarchyItem item = prepareResult.get(0);
		TypeHierarchySubtypesParams subtypesParams = new TypeHierarchySubtypesParams(item);
		List<TypeHierarchyItem> subtypes = services.typeHierarchySubtypes(subtypesParams).get();
		
		Assertions.assertNotNull(subtypes);
		Assertions.assertEquals(2, subtypes.size(), "Should find 2 implementing classes");
	}
}
