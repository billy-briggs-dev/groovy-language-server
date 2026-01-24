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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import groovy.lang.GroovyClassLoader;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import io.github.classgraph.ScanResult;
import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.control.GroovyLSCompilationUnit;
import net.prominic.groovyls.config.ICompilationUnitFactory;
import net.prominic.groovyls.providers.CompletionProvider;
import net.prominic.groovyls.providers.DefinitionProvider;
import net.prominic.groovyls.providers.DocumentSymbolProvider;
import net.prominic.groovyls.providers.FormattingProvider;
import net.prominic.groovyls.providers.FormattingSettings;
import net.prominic.groovyls.providers.HoverProvider;
import net.prominic.groovyls.providers.ReferenceProvider;
import net.prominic.groovyls.providers.RenameProvider;
import net.prominic.groovyls.providers.SignatureHelpProvider;
import net.prominic.groovyls.providers.TypeDefinitionProvider;
import net.prominic.groovyls.providers.WorkspaceSymbolProvider;
import net.prominic.groovyls.util.FileContentsTracker;
import net.prominic.groovyls.util.GradleClasspathResolver;
import net.prominic.groovyls.util.GradleProjectDetector;
import net.prominic.groovyls.util.GradleProjectInfo;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;
import net.prominic.lsp.utils.Positions;

public class GroovyServices implements TextDocumentService, WorkspaceService, LanguageClientAware {
	private static class DiagnosticsResult {
		private final Set<PublishDiagnosticsParams> diagnostics;
		private final Set<URI> fatalErrorUris;

		private DiagnosticsResult(Set<PublishDiagnosticsParams> diagnostics, Set<URI> fatalErrorUris) {
			this.diagnostics = diagnostics;
			this.fatalErrorUris = fatalErrorUris;
		}
	}

	private static final Pattern PATTERN_CONSTRUCTOR_CALL = Pattern.compile(".*new \\w*$");
	private static final long DIAGNOSTIC_DEBOUNCE_MS = 250;

	private LanguageClient languageClient;

	private Path workspaceRoot;
	private ICompilationUnitFactory compilationUnitFactory;
	private GroovyLSCompilationUnit compilationUnit;
	private ASTNodeVisitor astVisitor;
	private Map<URI, List<Diagnostic>> prevDiagnosticsByFile;
	private FileContentsTracker fileContentsTracker = new FileContentsTracker();
	private ScanResult classGraphScanResult = null;
	private GroovyClassLoader classLoader = null;
	private URI previousContext = null;
	private GradleProjectInfo gradleProjectInfo;
	private List<String> userClasspathList = new ArrayList<>();
	private List<String> gradleClasspathList = Collections.emptyList();
	private final ScheduledExecutorService compileScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "groovyls-compile");
		thread.setDaemon(true);
		return thread;
	});
	private final Object compileLock = new Object();
	private ScheduledFuture<?> pendingCompile;
	private URI pendingContextUri;
	private final FormattingSettings formattingSettings = new FormattingSettings();
	private final FormattingProvider formattingProvider = new FormattingProvider(fileContentsTracker, formattingSettings);

	public GroovyServices(ICompilationUnitFactory factory) {
		compilationUnitFactory = factory;
	}

	public void setWorkspaceRoot(Path workspaceRoot) {
		this.workspaceRoot = workspaceRoot;
		createOrUpdateCompilationUnit();
		detectGradleProject();
	}

	@Override
	public void connect(LanguageClient client) {
		languageClient = client;
	}

	// --- NOTIFICATIONS

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		fileContentsTracker.didOpen(params);
		URI uri = URI.create(params.getTextDocument().getUri());
		scheduleCompileAndVisitAST(uri);
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		fileContentsTracker.didChange(params);
		URI uri = URI.create(params.getTextDocument().getUri());
		scheduleCompileAndVisitAST(uri);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		fileContentsTracker.didClose(params);
		URI uri = URI.create(params.getTextDocument().getUri());
		scheduleCompileAndVisitAST(uri);
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (!formattingSettings.isFormatOnSave() || languageClient == null) {
			return;
		}
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(params.getTextDocument().getUri());
		List<TextEdit> edits = formattingProvider.provideDocumentFormatting(textDocument).join();
		if (edits.isEmpty()) {
			return;
		}
		WorkspaceEdit workspaceEdit = new WorkspaceEdit();
		workspaceEdit.setChanges(Collections.singletonMap(textDocument.getUri(), edits));
		languageClient.applyEdit(new org.eclipse.lsp4j.ApplyWorkspaceEditParams(workspaceEdit));
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		detectGradleProject();
		boolean isSameUnit = createOrUpdateCompilationUnit();
		Set<URI> urisWithChanges = params.getChanges().stream().map(fileEvent -> URI.create(fileEvent.getUri()))
				.collect(Collectors.toSet());
		compile();
		if (isSameUnit) {
			visitAST(urisWithChanges);
		} else {
			visitAST();
		}
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		if (!(params.getSettings() instanceof JsonObject)) {
			return;
		}
		JsonObject settings = (JsonObject) params.getSettings();
		this.updateClasspath(settings);
		this.updateFormattingSettings(settings);
	}

	private void updateClasspath(JsonObject settings) {
		List<String> classpathList = new ArrayList<>();

		if (settings.has("groovy") && settings.get("groovy").isJsonObject()) {
			JsonObject groovy = settings.get("groovy").getAsJsonObject();
			if (groovy.has("classpath") && groovy.get("classpath").isJsonArray()) {
				JsonArray classpath = groovy.get("classpath").getAsJsonArray();
				classpath.forEach(element -> {
					classpathList.add(element.getAsString());
				});
			}
		}

		userClasspathList = classpathList;
		applyEffectiveClasspath();
	}

	private void updateFormattingSettings(JsonObject settings) {
		if (!settings.has("groovy") || !settings.get("groovy").isJsonObject()) {
			return;
		}
		JsonObject groovy = settings.get("groovy").getAsJsonObject();
		if (!groovy.has("formatting") || !groovy.get("formatting").isJsonObject()) {
			return;
		}
		JsonObject formatting = groovy.get("formatting").getAsJsonObject();
		if (formatting.has("indentSize") && formatting.get("indentSize").isJsonPrimitive()) {
			int indentSize = formatting.get("indentSize").getAsInt();
			if (indentSize > 0) {
				formattingSettings.setIndentSize(indentSize);
			}
		}
		if (formatting.has("braceStyle") && formatting.get("braceStyle").isJsonPrimitive()) {
			String braceStyle = formatting.get("braceStyle").getAsString();
			if ("nextLine".equalsIgnoreCase(braceStyle)) {
				formattingSettings.setBraceStyle(FormattingSettings.BraceStyle.NEXT_LINE);
			} else {
				formattingSettings.setBraceStyle(FormattingSettings.BraceStyle.SAME_LINE);
			}
		}
		if (formatting.has("spaceAroundOperators") && formatting.get("spaceAroundOperators").isJsonPrimitive()) {
			formattingSettings.setSpaceAroundOperators(formatting.get("spaceAroundOperators").getAsBoolean());
		}
		if (formatting.has("spaceAfterCommas") && formatting.get("spaceAfterCommas").isJsonPrimitive()) {
			formattingSettings.setSpaceAfterCommas(formatting.get("spaceAfterCommas").getAsBoolean());
		}
		if (formatting.has("spaceInsideBraces") && formatting.get("spaceInsideBraces").isJsonPrimitive()) {
			formattingSettings.setSpaceInsideBraces(formatting.get("spaceInsideBraces").getAsBoolean());
		}
		if (formatting.has("formatOnSave") && formatting.get("formatOnSave").isJsonPrimitive()) {
			formattingSettings.setFormatOnSave(formatting.get("formatOnSave").getAsBoolean());
		}
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return formattingProvider.provideDocumentFormatting(params.getTextDocument());
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		return formattingProvider.provideRangeFormatting(params);
	}

	// --- REQUESTS

	@Override
	public CompletableFuture<Hover> hover(HoverParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		HoverProvider provider = new HoverProvider(astVisitor);
		return provider.provideHover(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();
		URI uri = URI.create(textDocument.getUri());

		ensureCompiledForRequest(uri);

		String originalSource = null;
		ASTNode offsetNode = astVisitor.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		if (offsetNode == null) {
			originalSource = fileContentsTracker.getContents(uri);
			VersionedTextDocumentIdentifier versionedTextDocument = new VersionedTextDocumentIdentifier(
					textDocument.getUri(), 1);
			int offset = Positions.getOffset(originalSource, position);
			String lineBeforeOffset = originalSource.substring(offset - position.getCharacter(), offset);
			Matcher matcher = PATTERN_CONSTRUCTOR_CALL.matcher(lineBeforeOffset);
			TextDocumentContentChangeEvent changeEvent = null;
			if (matcher.matches()) {
				changeEvent = new TextDocumentContentChangeEvent(new Range(position, position), 0, "a()");
			} else {
				changeEvent = new TextDocumentContentChangeEvent(new Range(position, position), 0, "a");
			}
			DidChangeTextDocumentParams didChangeParams = new DidChangeTextDocumentParams(versionedTextDocument,
					Collections.singletonList(changeEvent));
			// if the offset node is null, there is probably a syntax error.
			// a completion request is usually triggered by the . character, and
			// if there is no property name after the dot, it will cause a syntax
			// error.
			// this hack adds a placeholder property name in the hopes that it
			// will correctly create a PropertyExpression to use for completion.
			// we'll restore the original text after we're done handling the
			// completion request.
			applyDidChangeAndCompileNow(didChangeParams);
		}

		CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = null;
		try {
			CompletionProvider provider = new CompletionProvider(astVisitor, classGraphScanResult, fileContentsTracker);
			result = provider.provideCompletion(params.getTextDocument(), params.getPosition(), params.getContext());
		} finally {
			if (originalSource != null) {
				VersionedTextDocumentIdentifier versionedTextDocument = new VersionedTextDocumentIdentifier(
						textDocument.getUri(), 1);
				TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(null, 0,
						originalSource);
				DidChangeTextDocumentParams didChangeParams = new DidChangeTextDocumentParams(versionedTextDocument,
						Collections.singletonList(changeEvent));
				applyDidChangeAndCompileNow(didChangeParams);
			}
		}

		return result;
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			DefinitionParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		DefinitionProvider provider = new DefinitionProvider(astVisitor);
		return provider.provideDefinition(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();
		URI uri = URI.create(textDocument.getUri());

		ensureCompiledForRequest(uri);

		String originalSource = null;
		ASTNode offsetNode = astVisitor.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		if (offsetNode == null) {
			originalSource = fileContentsTracker.getContents(uri);
			VersionedTextDocumentIdentifier versionedTextDocument = new VersionedTextDocumentIdentifier(
					textDocument.getUri(), 1);
			TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(
					new Range(position, position), 0, ")");
			DidChangeTextDocumentParams didChangeParams = new DidChangeTextDocumentParams(versionedTextDocument,
					Collections.singletonList(changeEvent));
			// if the offset node is null, there is probably a syntax error.
			// a signature help request is usually triggered by the ( character,
			// and if there is no matching ), it will cause a syntax error.
			// this hack adds a placeholder ) character in the hopes that it
			// will correctly create a ArgumentListExpression to use for
			// signature help.
			// we'll restore the original text after we're done handling the
			// signature help request.
			applyDidChangeAndCompileNow(didChangeParams);
		}

		try {
			SignatureHelpProvider provider = new SignatureHelpProvider(astVisitor);
			return provider.provideSignatureHelp(params.getTextDocument(), params.getPosition());
		} finally {
			if (originalSource != null) {
				VersionedTextDocumentIdentifier versionedTextDocument = new VersionedTextDocumentIdentifier(
						textDocument.getUri(), 1);
				TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(null, 0,
						originalSource);
				DidChangeTextDocumentParams didChangeParams = new DidChangeTextDocumentParams(versionedTextDocument,
						Collections.singletonList(changeEvent));
				applyDidChangeAndCompileNow(didChangeParams);
			}
		}
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(
			TypeDefinitionParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		TypeDefinitionProvider provider = new TypeDefinitionProvider(astVisitor);
		return provider.provideTypeDefinition(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		ReferenceProvider provider = new ReferenceProvider(astVisitor);
		return provider.provideReferences(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
			DocumentSymbolParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		DocumentSymbolProvider provider = new DocumentSymbolProvider(astVisitor);
		return provider.provideDocumentSymbols(params.getTextDocument());
	}

	@Override
	public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(
			WorkspaceSymbolParams params) {
		ensureAstAvailable();
		WorkspaceSymbolProvider provider = new WorkspaceSymbolProvider(astVisitor);
		return provider.provideWorkspaceSymbols(params.getQuery()).thenApply(Either::forLeft);
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		RenameProvider provider = new RenameProvider(astVisitor, fileContentsTracker);
		return provider.provideRename(params);
	}

	// --- INTERNAL

	private void visitAST() {
		if (compilationUnit == null) {
			return;
		}
		astVisitor = new ASTNodeVisitor();
		astVisitor.visitCompilationUnit(compilationUnit);
	}

	private void visitAST(Set<URI> uris) {
		if (astVisitor == null) {
			visitAST();
			return;
		}
		if (compilationUnit == null) {
			return;
		}
		astVisitor.visitCompilationUnit(compilationUnit, uris);
	}

	private boolean createOrUpdateCompilationUnit() {
		if (compilationUnit != null) {
			File targetDirectory = compilationUnit.getConfiguration().getTargetDirectory();
			if (targetDirectory != null && targetDirectory.exists()) {
				try {
					Files.walk(targetDirectory.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile)
							.forEach(File::delete);
				} catch (IOException e) {
					System.err.println("Failed to delete target directory: " + targetDirectory.getAbsolutePath());
					compilationUnit = null;
					return false;
				}
			}
		}

		GroovyLSCompilationUnit oldCompilationUnit = compilationUnit;
		compilationUnit = compilationUnitFactory.create(workspaceRoot, fileContentsTracker);
		fileContentsTracker.resetChangedFiles();

		if (compilationUnit != null) {
			File targetDirectory = compilationUnit.getConfiguration().getTargetDirectory();
			if (targetDirectory != null && !targetDirectory.exists() && !targetDirectory.mkdirs()) {
				System.err.println("Failed to create target directory: " + targetDirectory.getAbsolutePath());
			}
			GroovyClassLoader newClassLoader = compilationUnit.getClassLoader();
			if (!newClassLoader.equals(classLoader)) {
				classLoader = newClassLoader;

				try {
					classGraphScanResult = new ClassGraph().overrideClassLoaders(classLoader).enableClassInfo()
							.enableSystemJarsAndModules()
							.scan();
				} catch (ClassGraphException e) {
					classGraphScanResult = null;
				}
			}
		} else {
			classGraphScanResult = null;
		}

		return compilationUnit != null && compilationUnit.equals(oldCompilationUnit);
	}

	protected void recompileIfContextChanged(URI newContext) {
		if (previousContext == null || previousContext.equals(newContext)) {
			return;
		}
		fileContentsTracker.forceChanged(newContext);
		compileAndVisitAST(newContext);
	}

	private void ensureCompiledForRequest(URI contextURI) {
		if (contextURI == null) {
			return;
		}
		boolean shouldCompileNow = false;
		synchronized (compileLock) {
			if (pendingCompile != null) {
				pendingCompile.cancel(false);
				pendingCompile = null;
				shouldCompileNow = true;
			}
		}
		if (astVisitor == null || compilationUnit == null || shouldCompileNow) {
			compileAndVisitAST(contextURI);
			return;
		}
		recompileIfContextChanged(contextURI);
	}

	private void ensureAstAvailable() {
		if (astVisitor != null) {
			return;
		}
		if (createOrUpdateCompilationUnit()) {
			compile();
			visitAST();
		} else if (compilationUnit != null) {
			compile();
			visitAST();
		}
	}

	private void detectGradleProject() {
		gradleProjectInfo = GradleProjectDetector.detect(workspaceRoot);
		gradleClasspathList = GradleClasspathResolver.resolve(gradleProjectInfo);
		applyEffectiveClasspath();
	}

	private void applyEffectiveClasspath() {
		List<String> merged = GradleClasspathResolver.mergeClasspath(userClasspathList, gradleClasspathList);
		List<String> existing = compilationUnitFactory.getAdditionalClasspathList();
		if (existing == null) {
			existing = Collections.emptyList();
		}
		if (!merged.equals(existing)) {
			compilationUnitFactory.setAdditionalClasspathList(merged);

			createOrUpdateCompilationUnit();
			compile();
			visitAST();
			previousContext = null;
		}
	}

	private void compileAndVisitAST(URI contextURI) {
		if (contextURI == null) {
			return;
		}
		Set<URI> uris = Collections.singleton(contextURI);
		boolean isSameUnit = createOrUpdateCompilationUnit();
		compile();
		if (isSameUnit) {
			visitAST(uris);
		} else {
			visitAST();
		}
		previousContext = contextURI;
	}

	private void scheduleCompileAndVisitAST(URI contextURI) {
		synchronized (compileLock) {
			pendingContextUri = contextURI;
			if (pendingCompile != null) {
				pendingCompile.cancel(false);
			}
			pendingCompile = compileScheduler.schedule(() -> {
				URI uriToCompile;
				synchronized (compileLock) {
					uriToCompile = pendingContextUri;
					pendingCompile = null;
				}
				compileAndVisitAST(uriToCompile);
			}, DIAGNOSTIC_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void applyDidChangeAndCompileNow(DidChangeTextDocumentParams params) {
		fileContentsTracker.didChange(params);
		URI uri = URI.create(params.getTextDocument().getUri());
		compileAndVisitAST(uri);
	}

	private void compile() {
		if (compilationUnit == null) {
			return;
		}
		try {
			// AST is completely built after the canonicalization phase
			// for code intelligence, we shouldn't need to go further
			// http://groovy-lang.org/metaprogramming.html#_compilation_phases_guide
			compilationUnit.compile(Phases.CANONICALIZATION);
		} catch (CompilationFailedException e) {
			// ignore
		} catch (GroovyBugError e) {
			System.err.println("Unexpected exception in language server when compiling Groovy.");
			e.printStackTrace(System.err);
		} catch (Exception e) {
			System.err.println("Unexpected exception in language server when compiling Groovy.");
			e.printStackTrace(System.err);
		}
		DiagnosticsResult diagnostics = handleErrorCollector(compilationUnit.getErrorCollector());
		diagnostics.diagnostics.stream().forEach(languageClient::publishDiagnostics);
		removeFatalErrorSources(diagnostics.fatalErrorUris);
	}

	private DiagnosticsResult handleErrorCollector(ErrorCollector collector) {
		Map<URI, List<Diagnostic>> diagnosticsByFile = new HashMap<>();
		Set<URI> fatalErrorUris = new HashSet<>();

		List<? extends Message> errors = collector.getErrors();
		if (errors != null) {
			errors.stream().filter((Object message) -> message instanceof SyntaxErrorMessage)
					.forEach((Object message) -> {
						SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
						SyntaxException cause = syntaxErrorMessage.getCause();
						Range range = GroovyLanguageServerUtils.syntaxExceptionToRange(cause);
						if (range == null) {
							// range can't be null in a Diagnostic, so we need
							// a fallback
							range = new Range(new Position(0, 0), new Position(0, 0));
						}
						Diagnostic diagnostic = new Diagnostic();
						diagnostic.setRange(range);
						diagnostic.setSeverity(cause.isFatal() ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning);
						diagnostic.setMessage(cause.getMessage());
						URI uri = null;
						boolean hasSourceLocator = false;
						try {
							String sourceLocator = cause.getSourceLocator();
							if (sourceLocator != null && !sourceLocator.isBlank()) {
								uri = Paths.get(sourceLocator).toUri();
								hasSourceLocator = true;
							}
						} catch (Exception e) {
							// ignore malformed source locators
						}
						if (uri == null) {
							uri = previousContext;
						}
						if (uri != null) {
							diagnosticsByFile.computeIfAbsent(uri, (key) -> new ArrayList<>()).add(diagnostic);
							if (hasSourceLocator) {
								fatalErrorUris.add(uri);
							}
						}
					});
		}

		ASTNodeVisitor diagnosticsVisitor = astVisitor;
		if (diagnosticsVisitor == null && compilationUnit != null) {
			diagnosticsVisitor = new ASTNodeVisitor();
			diagnosticsVisitor.visitCompilationUnit(compilationUnit);
		}
		collectUndefinedVariableDiagnostics(diagnosticsByFile, diagnosticsVisitor);

		Set<PublishDiagnosticsParams> result = diagnosticsByFile.entrySet().stream()
				.map(entry -> new PublishDiagnosticsParams(entry.getKey().toString(), entry.getValue()))
				.collect(Collectors.toSet());

		if (prevDiagnosticsByFile != null) {
			for (URI key : prevDiagnosticsByFile.keySet()) {
				if (!diagnosticsByFile.containsKey(key)) {
					// send an empty list of diagnostics for files that had
					// diagnostics previously or they won't be cleared
					result.add(new PublishDiagnosticsParams(key.toString(), new ArrayList<>()));
				}
			}
		}
		prevDiagnosticsByFile = diagnosticsByFile;
		return new DiagnosticsResult(result, fatalErrorUris);
	}

	private void collectUndefinedVariableDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile,
			ASTNodeVisitor visitor) {
		if (visitor == null) {
			return;
		}
		for (ASTNode node : visitor.getNodes()) {
			if (!(node instanceof VariableExpression)) {
				if (node instanceof PropertyExpression) {
					PropertyExpression propExpr = (PropertyExpression) node;
					Expression objectExpr = propExpr.getObjectExpression();
					if (objectExpr instanceof VariableExpression) {
						VariableExpression objVar = (VariableExpression) objectExpr;
						if ("this".equals(objVar.getName())) {
							String propName = propExpr.getPropertyAsString();
							if (propName != null && !hasEnclosingMember(propName, objVar, visitor)) {
								URI uri = visitor.getURI(propExpr);
								Range range = GroovyLanguageServerUtils.astNodeToRange(propExpr.getProperty());
								if (uri != null && range != null) {
									Diagnostic diagnostic = new Diagnostic();
									diagnostic.setRange(range);
									diagnostic.setSeverity(DiagnosticSeverity.Warning);
									diagnostic.setMessage("Undefined variable: " + propName);
									diagnosticsByFile.computeIfAbsent(uri, key -> new ArrayList<>()).add(diagnostic);
								}
							}
						}
					}
				}
				continue;
			}
			VariableExpression variable = (VariableExpression) node;
			String name = variable.getName();
			if (name == null || name.isBlank()) {
				continue;
			}
			if (name.equals("this") || name.equals("super") || name.equals("it")
					|| name.equals("delegate") || name.equals("owner")) {
				continue;
			}
			Variable accessed = variable.getAccessedVariable();
			if (!(accessed instanceof DynamicVariable)) {
				continue;
			}
			if (isClassName(name, visitor)) {
				continue;
			}
			if (hasEnclosingMember(name, variable, visitor)) {
				continue;
			}
			URI uri = visitor.getURI(variable);
			if (uri == null) {
				continue;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(variable);
			if (range == null) {
				continue;
			}
			Diagnostic diagnostic = new Diagnostic();
			diagnostic.setRange(range);
			diagnostic.setSeverity(DiagnosticSeverity.Warning);
			diagnostic.setMessage("Undefined variable: " + name);
			diagnosticsByFile.computeIfAbsent(uri, key -> new ArrayList<>()).add(diagnostic);
		}
	}

	private boolean isClassName(String name, ASTNodeVisitor visitor) {
		if (visitor == null) {
			return false;
		}
		for (ClassNode classNode : visitor.getClassNodes()) {
			if (name.equals(classNode.getName()) || name.equals(classNode.getNameWithoutPackage())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasEnclosingMember(String name, VariableExpression variable, ASTNodeVisitor visitor) {
		ASTNode current = variable;
		while (current != null) {
			if (current instanceof ClassNode) {
				ClassNode classNode = (ClassNode) current;
				return classNode.getProperty(name) != null || classNode.getField(name) != null
						|| !classNode.getMethods(name).isEmpty();
			}
			current = visitor.getParent(current);
		}
		return false;
	}

	private void removeFatalErrorSources(Set<URI> fatalErrorUris) {
		if (compilationUnit == null || fatalErrorUris == null || fatalErrorUris.isEmpty()) {
			return;
		}
		List<SourceUnit> sourcesToRemove = new ArrayList<>();
		compilationUnit.iterator().forEachRemaining(sourceUnit -> {
			URI uri = sourceUnit.getSource().getURI();
			if (uri != null && fatalErrorUris.contains(uri)) {
				sourcesToRemove.add(sourceUnit);
			}
		});
		if (!sourcesToRemove.isEmpty()) {
			compilationUnit.removeSources(sourcesToRemove);
			try {
				compilationUnit.compile(Phases.CANONICALIZATION);
			} catch (Exception e) {
				// ignore
			}
		}
	}
}
