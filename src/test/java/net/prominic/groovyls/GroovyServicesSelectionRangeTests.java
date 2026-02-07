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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesSelectionRangeTests {
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
	void testSelectionRangeInMethod() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class SelectionTest {\n" +
				"    def method() {\n" +
				"        def x = 1\n" +
				"        return x\n" +
				"    }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("SelectionTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		// Position inside the method (line 4, column 8 - "def x = 1")
		List<Position> positions = new ArrayList<>();
		positions.add(new Position(4, 12)); // Inside "x"

		SelectionRangeParams params = new SelectionRangeParams(new TextDocumentIdentifier(uri.toString()), positions);
		List<SelectionRange> ranges = services.selectionRange(params).get();

		Assertions.assertNotNull(ranges);
		Assertions.assertFalse(ranges.isEmpty(), "Should return at least one selection range");

		SelectionRange range = ranges.get(0);
		Assertions.assertNotNull(range);
		Assertions.assertNotNull(range.getRange());
		
		// Should have parent ranges (nested selection)
		Assertions.assertNotNull(range.getParent(), "Should have parent selection range");
	}

	@Test
	void testSelectionRangeMultiplePositions() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class MultiTest {\n" +
				"    def method1() { }\n" +
				"    def method2() { }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("MultiTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		// Multiple positions
		List<Position> positions = new ArrayList<>();
		positions.add(new Position(3, 10)); // In method1
		positions.add(new Position(4, 10)); // In method2

		SelectionRangeParams params = new SelectionRangeParams(new TextDocumentIdentifier(uri.toString()), positions);
		List<SelectionRange> ranges = services.selectionRange(params).get();

		Assertions.assertNotNull(ranges);
		Assertions.assertEquals(2, ranges.size(), "Should return selection ranges for both positions");
	}

	@Test
	void testSelectionRangeNested() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class NestedTest {\n" +
				"    def outer() {\n" +
				"        def x = 1\n" +
				"    }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("NestedTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		// Position inside the method body
		List<Position> positions = new ArrayList<>();
		positions.add(new Position(4, 12));

		SelectionRangeParams params = new SelectionRangeParams(new TextDocumentIdentifier(uri.toString()), positions);
		List<SelectionRange> ranges = services.selectionRange(params).get();

		Assertions.assertNotNull(ranges);
		Assertions.assertFalse(ranges.isEmpty());

		// Check that we have nested ranges
		SelectionRange current = ranges.get(0);
		int depth = 0;
		while (current != null) {
			depth++;
			current = current.getParent();
		}

		Assertions.assertTrue(depth >= 2, "Should have at least 2 levels of nesting (statement and containing node)");
	}
}
