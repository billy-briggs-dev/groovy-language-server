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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.config.CompilationUnitFactory;

class GroovyServicesDiagnosticsTests {
	private static final String LANGUAGE_GROOVY = "groovy";
	private static final String PATH_WORKSPACE = "./build/test_workspace/";
	private static final String PATH_SRC = "./src/main/groovy";

	private GroovyServices services;
	private Path workspaceRoot;
	private Path srcRoot;
	private AtomicInteger publishCount;
	private AtomicReference<PublishDiagnosticsParams> lastDiagnostics;
	private CountDownLatch publishLatch;

	@BeforeEach
	void setup() {
		workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
		srcRoot = workspaceRoot.resolve(PATH_SRC);
		if (!Files.exists(srcRoot)) {
			srcRoot.toFile().mkdirs();
		}

		publishCount = new AtomicInteger();
		lastDiagnostics = new AtomicReference<>();
		publishLatch = new CountDownLatch(1);

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
				lastDiagnostics.set(diagnostics);
				publishCount.incrementAndGet();
				publishLatch.countDown();
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
	void testDiagnosticsDebouncedOnRapidChanges() throws Exception {
		Path filePath = srcRoot.resolve("Diagnostics.groovy");
		String uri = filePath.toUri().toString();

		String validSource = "class Diagnostics { }";
		TextDocumentItem textDocumentItem = new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, validSource);
		services.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

		String invalidSource1 = "class Diagnostics { def x = }";
		DidChangeTextDocumentParams changeParams1 = new DidChangeTextDocumentParams();
		changeParams1.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));
		changeParams1.setContentChanges(Collections
				.singletonList(new TextDocumentContentChangeEvent(null, 0, invalidSource1)));
		services.didChange(changeParams1);

		String invalidSource2 = "class Diagnostics { def y = }";
		DidChangeTextDocumentParams changeParams2 = new DidChangeTextDocumentParams();
		changeParams2.setTextDocument(new VersionedTextDocumentIdentifier(uri, 3));
		changeParams2.setContentChanges(Collections
				.singletonList(new TextDocumentContentChangeEvent(null, 0, invalidSource2)));
		services.didChange(changeParams2);

		boolean published = publishLatch.await(2, TimeUnit.SECONDS);
		Assertions.assertTrue(published, "Expected diagnostics to be published");
		Assertions.assertEquals(1, publishCount.get(), "Expected debounced diagnostics to publish once");
		PublishDiagnosticsParams diagnostics = lastDiagnostics.get();
		Assertions.assertNotNull(diagnostics);
		Assertions.assertEquals(uri, diagnostics.getUri());
		Assertions.assertFalse(diagnostics.getDiagnostics().isEmpty());
	}
}
