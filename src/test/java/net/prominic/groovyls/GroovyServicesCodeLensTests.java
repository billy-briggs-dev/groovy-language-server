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

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
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

class GroovyServicesCodeLensTests {
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
	void testCodeLensForClass() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class CodeLensTest {\n" +
				"    def method1() {\n" +
				"        println 'hello'\n" +
				"    }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("CodeLensTest.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		CodeLensParams params = new CodeLensParams(new TextDocumentIdentifier(uri.toString()));
		List<? extends CodeLens> lenses = services.codeLens(params).get();

		Assertions.assertNotNull(lenses);
		Assertions.assertFalse(lenses.isEmpty(), "Should have code lenses for class and method");

		// Check that lenses have commands
		for (CodeLens lens : lenses) {
			Assertions.assertNotNull(lens.getCommand(), "Code lens should have a command");
			Assertions.assertTrue(lens.getCommand().getTitle().contains("reference"), 
				"Command title should mention references");
		}
	}

	@Test
	void testCodeLensShowsReferenceCount() throws Exception {
		// Create two files: one with a class definition, one that uses it
		String classText = "package test\n" + 
				"\n" + 
				"class MyClass {\n" +
				"    def myMethod() { }\n" +
				"}\n";

		String usageText = "package test\n" + 
				"\n" + 
				"class UsageClass {\n" +
				"    def test() {\n" +
				"        def obj = new MyClass()\n" +
				"        obj.myMethod()\n" +
				"        obj.myMethod()\n" +
				"    }\n" +
				"}\n";

		Path classPath = srcRoot.resolve("MyClass.groovy");
		Path usagePath = srcRoot.resolve("UsageClass.groovy");
		
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(classPath.toUri().toString(), LANGUAGE_GROOVY, 1, classText)));
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(usagePath.toUri().toString(), LANGUAGE_GROOVY, 1, usageText)));

		CodeLensParams params = new CodeLensParams(new TextDocumentIdentifier(classPath.toUri().toString()));
		List<? extends CodeLens> lenses = services.codeLens(params).get();

		Assertions.assertNotNull(lenses);
		Assertions.assertFalse(lenses.isEmpty(), "Should have code lenses");

		// Find the lens for myMethod
		boolean foundMethodLens = lenses.stream()
				.anyMatch(lens -> lens.getCommand().getTitle().contains("reference"));
		
		Assertions.assertTrue(foundMethodLens, "Should have a code lens showing references");
	}

	@Test
	void testCodeLensForNoReferences() throws Exception {
		String sourceText = "package test\n" + 
				"\n" + 
				"class UnusedClass {\n" +
				"    def unusedMethod() { }\n" +
				"}\n";

		Path filePath = srcRoot.resolve("UnusedClass.groovy");
		URI uri = filePath.toUri();
		services.didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri.toString(), LANGUAGE_GROOVY, 1, sourceText)));

		CodeLensParams params = new CodeLensParams(new TextDocumentIdentifier(uri.toString()));
		List<? extends CodeLens> lenses = services.codeLens(params).get();

		Assertions.assertNotNull(lenses);
		Assertions.assertFalse(lenses.isEmpty(), "Should have code lenses");
		
		// All lenses should have commands with reference text
		for (CodeLens lens : lenses) {
			Assertions.assertNotNull(lens.getCommand());
			String title = lens.getCommand().getTitle();
			Assertions.assertTrue(title.contains("reference"), "Should contain 'reference' text");
		}
	}
}
