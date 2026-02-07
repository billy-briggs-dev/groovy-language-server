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

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
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

class GroovyServicesCallHierarchyTests {
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
	void testPrepareCallHierarchy() throws Exception {
		Path filePath = srcRoot.resolve("CallHierarchy.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package calls\n");
		contentBuilder.append("class MyClass {\n");
		contentBuilder.append("  void methodA() {\n");
		contentBuilder.append("    methodB()\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("  void methodB() {\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(2, 7); // On "methodA"
		CallHierarchyPrepareParams params = new CallHierarchyPrepareParams(textDocument, position);
		
		List<CallHierarchyItem> result = services.prepareCallHierarchy(params).get();
		
		Assertions.assertNotNull(result);
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("methodA", result.get(0).getName());
	}

	@Test
	void testCallHierarchyOutgoingCalls() throws Exception {
		Path filePath = srcRoot.resolve("OutgoingCalls.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package calls\n");
		contentBuilder.append("class MyClass {\n");
		contentBuilder.append("  void caller() {\n");
		contentBuilder.append("    callee1()\n");
		contentBuilder.append("    callee2()\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("  void callee1() {}\n");
		contentBuilder.append("  void callee2() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(2, 7); // On "caller"
		CallHierarchyPrepareParams prepareParams = new CallHierarchyPrepareParams(textDocument, position);
		
		List<CallHierarchyItem> prepareResult = services.prepareCallHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		CallHierarchyItem item = prepareResult.get(0);
		CallHierarchyOutgoingCallsParams outgoingParams = new CallHierarchyOutgoingCallsParams(item);
		List<CallHierarchyOutgoingCall> outgoingCalls = services.callHierarchyOutgoingCalls(outgoingParams).get();
		
		Assertions.assertNotNull(outgoingCalls);
		Assertions.assertEquals(2, outgoingCalls.size(), "Should find 2 outgoing calls");
	}

	@Test
	void testCallHierarchyIncomingCalls() throws Exception {
		Path filePath = srcRoot.resolve("IncomingCalls.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package calls\n");
		contentBuilder.append("class MyClass {\n");
		contentBuilder.append("  void caller1() {\n");
		contentBuilder.append("    callee()\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("  void caller2() {\n");
		contentBuilder.append("    callee()\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("  void callee() {}\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(8, 7); // On "callee"
		CallHierarchyPrepareParams prepareParams = new CallHierarchyPrepareParams(textDocument, position);
		
		List<CallHierarchyItem> prepareResult = services.prepareCallHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		CallHierarchyItem item = prepareResult.get(0);
		CallHierarchyIncomingCallsParams incomingParams = new CallHierarchyIncomingCallsParams(item);
		List<CallHierarchyIncomingCall> incomingCalls = services.callHierarchyIncomingCalls(incomingParams).get();
		
		Assertions.assertNotNull(incomingCalls);
		Assertions.assertEquals(2, incomingCalls.size(), "Should find 2 incoming calls");
	}

	@Test
	void testCallHierarchyWithNoOutgoingCalls() throws Exception {
		Path filePath = srcRoot.resolve("NoOutgoingCalls.groovy");
		String uri = filePath.toUri().toString();
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("package calls\n");
		contentBuilder.append("class MyClass {\n");
		contentBuilder.append("  void leafMethod() {\n");
		contentBuilder.append("    // No outgoing calls\n");
		contentBuilder.append("  }\n");
		contentBuilder.append("}\n");
		String content = contentBuilder.toString();
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, content);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));
		
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		Position position = new Position(2, 7); // On "leafMethod"
		CallHierarchyPrepareParams prepareParams = new CallHierarchyPrepareParams(textDocument, position);
		
		List<CallHierarchyItem> prepareResult = services.prepareCallHierarchy(prepareParams).get();
		Assertions.assertEquals(1, prepareResult.size());
		
		CallHierarchyItem item = prepareResult.get(0);
		CallHierarchyOutgoingCallsParams outgoingParams = new CallHierarchyOutgoingCallsParams(item);
		List<CallHierarchyOutgoingCall> outgoingCalls = services.callHierarchyOutgoingCalls(outgoingParams).get();
		
		Assertions.assertNotNull(outgoingCalls);
		Assertions.assertEquals(0, outgoingCalls.size(), "Should find no outgoing calls");
	}
}
