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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
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

class GroovyServicesCodeActionTests {
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
	void testConvertSingleLineToMultiLineString() throws Exception {
		Path filePath = srcRoot.resolve("CodeActions.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class CodeActions {\n");
		contents.append("  def myMethod() {\n");
		contents.append("    def str = \"hello world\"\n");
		contents.append("  }\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		// Position at the string literal
		Position position = new Position(2, 16);
		Range range = new Range(position, position);

		CodeActionParams params = new CodeActionParams(textDocument, range, new CodeActionContext());

		CompletableFuture<List<Either<Command, CodeAction>>> future = services.codeAction(params);
		List<Either<Command, CodeAction>> codeActions = future.get();

		Assertions.assertNotNull(codeActions);
		Assertions.assertTrue(codeActions.size() > 0, "Expected at least one code action");

		// Find the convert string action
		CodeAction convertAction = codeActions.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.filter(action -> action.getTitle().contains("multi-line"))
				.findFirst()
				.orElse(null);

		Assertions.assertNotNull(convertAction, "Expected to find 'Convert to multi-line string' action");
		Assertions.assertNotNull(convertAction.getEdit(), "Expected the action to have an edit");
	}

	@Test
	void testConvertMultiLineToSingleLineString() throws Exception {
		Path filePath = srcRoot.resolve("CodeActions2.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class CodeActions2 {\n");
		contents.append("  def myMethod() {\n");
		contents.append("    def str = \"\"\"hello world\"\"\"\n");
		contents.append("  }\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		// Position at the multi-line string literal
		Position position = new Position(2, 16);
		Range range = new Range(position, position);

		CodeActionParams params = new CodeActionParams(textDocument, range, new CodeActionContext());

		CompletableFuture<List<Either<Command, CodeAction>>> future = services.codeAction(params);
		List<Either<Command, CodeAction>> codeActions = future.get();

		Assertions.assertNotNull(codeActions);
		Assertions.assertTrue(codeActions.size() > 0, "Expected at least one code action");

		// Find the convert string action
		CodeAction convertAction = codeActions.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.filter(action -> action.getTitle().contains("single-line"))
				.findFirst()
				.orElse(null);

		Assertions.assertNotNull(convertAction, "Expected to find 'Convert to single-line string' action");
		Assertions.assertNotNull(convertAction.getEdit(), "Expected the action to have an edit");
	}

	@Test
	void testCodeActionsReturnsEmptyForNonString() throws Exception {
		Path filePath = srcRoot.resolve("CodeActions3.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class CodeActions3 {\n");
		contents.append("  def myMethod() {\n");
		contents.append("    def num = 42\n");
		contents.append("  }\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		// Position at the number literal
		Position position = new Position(2, 16);
		Range range = new Range(position, position);

		CodeActionParams params = new CodeActionParams(textDocument, range, new CodeActionContext());

		CompletableFuture<List<Either<Command, CodeAction>>> future = services.codeAction(params);
		List<Either<Command, CodeAction>> codeActions = future.get();

		Assertions.assertNotNull(codeActions);
		// Should not have a convert string action for a number
		long convertStringActions = codeActions.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.filter(action -> action.getTitle().contains("string"))
				.count();

		Assertions.assertEquals(0, convertStringActions, "Should not have string conversion actions for numbers");
	}
}
