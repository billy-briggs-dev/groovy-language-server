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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import net.prominic.groovyls.config.CompilationUnitFactory;
import net.prominic.groovyls.config.ICompilationUnitFactory;

public class GroovyLanguageServer implements LanguageServer, LanguageClientAware {

    public static void main(String[] args) {
        InputStream systemIn = System.in;
        OutputStream systemOut = System.out;
        // redirect System.out to System.err because we need to prevent
        // System.out from receiving anything that isn't an LSP message
        System.setOut(new PrintStream(System.err));
        GroovyLanguageServer server = new GroovyLanguageServer();
        Launcher<LanguageClient> launcher = Launcher.createLauncher(server, LanguageClient.class, systemIn, systemOut);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }

    private GroovyServices groovyServices;

    public GroovyLanguageServer() {
        this(new CompilationUnitFactory());
    }

    public GroovyLanguageServer(ICompilationUnitFactory compilationUnitFactory) {
        this.groovyServices = new GroovyServices(compilationUnitFactory);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        Path workspaceRoot = null;
        if (params.getWorkspaceFolders() != null && !params.getWorkspaceFolders().isEmpty()) {
            WorkspaceFolder folder = params.getWorkspaceFolders().get(0);
            if (folder != null && folder.getUri() != null) {
                workspaceRoot = Paths.get(URI.create(folder.getUri()));
            }
        }
        if (workspaceRoot != null) {
            groovyServices.setWorkspaceRoot(workspaceRoot);
        }

        CompletionOptions completionOptions = new CompletionOptions(false, Arrays.asList("."));
        ServerCapabilities serverCapabilities = new ServerCapabilities();
        serverCapabilities.setCompletionProvider(completionOptions);
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        serverCapabilities.setDocumentSymbolProvider(true);
        serverCapabilities.setWorkspaceSymbolProvider(true);
        serverCapabilities.setDocumentSymbolProvider(true);
        serverCapabilities.setReferencesProvider(true);
        serverCapabilities.setDefinitionProvider(true);
        serverCapabilities.setTypeDefinitionProvider(true);
        serverCapabilities.setImplementationProvider(true);
        serverCapabilities.setTypeHierarchyProvider(true);
        serverCapabilities.setCallHierarchyProvider(true);
        serverCapabilities.setHoverProvider(true);
        serverCapabilities.setRenameProvider(true);
        serverCapabilities.setDocumentFormattingProvider(true);
        serverCapabilities.setDocumentRangeFormattingProvider(true);
        serverCapabilities.setFoldingRangeProvider(true);
        serverCapabilities.setSelectionRangeProvider(true);
        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        serverCapabilities.setSignatureHelpProvider(signatureHelpOptions);
        serverCapabilities.setExecuteCommandProvider(
                new ExecuteCommandOptions(Arrays.asList("groovy.findUsages", "groovy.goToSuperMethod")));

        InitializeResult initializeResult = new InitializeResult(serverCapabilities);
        return CompletableFuture.completedFuture(initializeResult);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return groovyServices;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return groovyServices;
    }

    @Override
    public void connect(LanguageClient client) {
        groovyServices.connect(client);
    }
}
