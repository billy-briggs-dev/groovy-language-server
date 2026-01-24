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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
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

class GroovyServicesErrorIsolationTests {
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
    void testSyntaxErrorInOneFileDoesNotBreakCompletionInAnother() throws Exception {
        Path badFile = srcRoot.resolve("BadFile.groovy");
        String badUri = badFile.toUri().toString();
        String badContents = "class BadFile { def x = }";
        TextDocumentItem badItem = new TextDocumentItem(badUri, LANGUAGE_GROOVY, 1, badContents);
        services.didOpen(new DidOpenTextDocumentParams(badItem));

        Path goodFile = srcRoot.resolve("GoodFile.groovy");
        String goodUri = goodFile.toUri().toString();
        StringBuilder goodContents = new StringBuilder();
        goodContents.append("class GoodFile {\n");
        goodContents.append("  void test() {\n");
        goodContents.append("    String localVar\n");
        goodContents.append("    loc\n");
        goodContents.append("  }\n");
        goodContents.append("}\n");
        TextDocumentItem goodItem = new TextDocumentItem(goodUri, LANGUAGE_GROOVY, 1, goodContents.toString());
        services.didOpen(new DidOpenTextDocumentParams(goodItem));

        TextDocumentIdentifier textDocument = new TextDocumentIdentifier(goodUri);
        Position position = new Position(3, 7);
        Either<List<CompletionItem>, CompletionList> result = services
                .completion(new CompletionParams(textDocument, position)).get();
        Assertions.assertTrue(result.isLeft());
        List<CompletionItem> items = result.getLeft();
        List<CompletionItem> filteredItems = items.stream().filter(item -> {
            return item.getLabel().equals("localVar") && item.getKind().equals(CompletionItemKind.Variable);
        }).collect(Collectors.toList());
        Assertions.assertEquals(1, filteredItems.size());
    }
}
