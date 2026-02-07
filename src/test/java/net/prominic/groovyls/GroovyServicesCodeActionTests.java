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
		workspaceRoot = null;
		srcRoot = null;
	}

	@Test
	void testGenerateGettersAndSetters() throws Exception {
		Path filePath = srcRoot.resolve("Person.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class Person {\n");
		contents.append("    private String name\n");
		contents.append("    private int age\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10); // Inside the class
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.size() > 0, "Expected at least one code action");
		
		// Check for getter/setter actions
		boolean foundGettersAndSetters = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("Getters and Setters"));
		Assertions.assertTrue(foundGettersAndSetters, "Expected 'Generate Getters and Setters' action");
	}

	@Test
	void testGenerateConstructor() throws Exception {
		Path filePath = srcRoot.resolve("Book.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class Book {\n");
		contents.append("    private String title\n");
		contents.append("    private String author\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10);
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		
		boolean foundConstructor = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("Constructor"));
		Assertions.assertTrue(foundConstructor, "Expected 'Generate Constructor' action");
	}

	@Test
	void testGenerateToString() throws Exception {
		Path filePath = srcRoot.resolve("Product.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class Product {\n");
		contents.append("    String name\n");
		contents.append("    double price\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10);
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		
		boolean foundToString = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("toString"));
		Assertions.assertTrue(foundToString, "Expected 'Generate toString()' action");
	}

	@Test
	void testGenerateEqualsHashCode() throws Exception {
		Path filePath = srcRoot.resolve("Employee.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class Employee {\n");
		contents.append("    String id\n");
		contents.append("    String department\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10);
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		
		boolean foundEqualsHashCode = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("equals") && action.getTitle().contains("hashCode"));
		Assertions.assertTrue(foundEqualsHashCode, "Expected 'Generate equals() and hashCode()' action");
	}

	@Test
	void testImplementInterfaceMethods() throws Exception {
		Path filePath = srcRoot.resolve("MyComparable.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class MyComparable implements Comparable<MyComparable> {\n");
		contents.append("    String value\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10);
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		
		boolean foundImplement = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("Implement Interface"));
		Assertions.assertTrue(foundImplement, "Expected 'Implement Interface Methods' action");
	}

	@Test
	void testOverrideMethods() throws Exception {
		Path filePath = srcRoot.resolve("CustomList.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contents = new StringBuilder();
		contents.append("class CustomList extends ArrayList {\n");
		contents.append("    // Empty class\n");
		contents.append("}\n");
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, contents.toString());
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(1, 10);
		Range range = new Range(position, position);
		CodeActionContext context = new CodeActionContext();
		CodeActionParams params = new CodeActionParams(textDocument, range, context);
		
		List<Either<Command, CodeAction>> result = services.codeAction(params).get();
		Assertions.assertNotNull(result);
		
		boolean foundOverride = result.stream()
				.filter(Either::isRight)
				.map(Either::getRight)
				.anyMatch(action -> action.getTitle().contains("Override"));
		Assertions.assertTrue(foundOverride, "Expected 'Override Methods' action");
	}
}
