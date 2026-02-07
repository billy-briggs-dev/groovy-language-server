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
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesFoldingRangeTests {
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
	}

	@Test
	void testFoldingRangeForClass() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class FoldingTest {\n" +
				"    def method1() {\n" +
				"        println 'hello'\n" +
				"    }\n" +
				"    \n" +
				"    def method2() {\n" +
				"        println 'world'\n" +
				"    }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("FoldingTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		FoldingRangeRequestParams params = new FoldingRangeRequestParams(new TextDocumentIdentifier(uri.toString()));
		List<FoldingRange> ranges = services.foldingRange(params).get();

		Assertions.assertNotNull(ranges);
		Assertions.assertTrue(ranges.size() >= 2, "Expected at least class and method folding ranges");

		// Check that we have at least one range for the class
		boolean hasClassRange = ranges.stream()
				.anyMatch(r -> r.getKind().equals(FoldingRangeKind.Region) && r.getStartLine() == 2);
		Assertions.assertTrue(hasClassRange, "Should have folding range for class");
	}

	@Test
	void testFoldingRangeForImports() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"import java.util.List\n" +
				"import java.util.Map\n" +
				"import java.util.Set\n" +
				"\n" +
				"class ImportTest {\n" +
				"}\n";

		Path filePath = srcRoot.resolve("ImportTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		FoldingRangeRequestParams params = new FoldingRangeRequestParams(new TextDocumentIdentifier(uri.toString()));
		List<FoldingRange> ranges = services.foldingRange(params).get();

		Assertions.assertNotNull(ranges);
		
		// Check that we have a folding range for imports
		boolean hasImportRange = ranges.stream()
				.anyMatch(r -> r.getKind().equals(FoldingRangeKind.Imports));
		Assertions.assertTrue(hasImportRange, "Should have folding range for imports");
	}

	@Test
	void testFoldingRangeForMethods() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class MethodTest {\n" +
				"    def longMethod() {\n" +
				"        def x = 1\n" +
				"        def y = 2\n" +
				"        return x + y\n" +
				"    }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("MethodTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		FoldingRangeRequestParams params = new FoldingRangeRequestParams(new TextDocumentIdentifier(uri.toString()));
		List<FoldingRange> ranges = services.foldingRange(params).get();

		Assertions.assertNotNull(ranges);
		
		// Check that we have at least one method range
		boolean hasMethodRange = ranges.stream()
				.anyMatch(r -> r.getKind().equals(FoldingRangeKind.Region) && r.getStartLine() == 3);
		Assertions.assertTrue(hasMethodRange, "Should have folding range for method");
	}
}
