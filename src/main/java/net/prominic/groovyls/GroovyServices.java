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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
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
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
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
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
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
import net.prominic.groovyls.providers.CallHierarchyProvider;
import net.prominic.groovyls.providers.CodeLensProvider;
import net.prominic.groovyls.providers.DefinitionProvider;
import net.prominic.groovyls.providers.DocumentSymbolProvider;
import net.prominic.groovyls.providers.FoldingRangeProvider;
import net.prominic.groovyls.providers.SelectionRangeProvider;
import net.prominic.groovyls.providers.GspTemplateSymbolProvider;
import net.prominic.groovyls.providers.ImplementationProvider;
import net.prominic.groovyls.providers.FormattingProvider;
import net.prominic.groovyls.providers.FormattingSettings;
import net.prominic.groovyls.providers.HoverProvider;
import net.prominic.groovyls.providers.ReferenceProvider;
import net.prominic.groovyls.providers.RenameProvider;
import net.prominic.groovyls.providers.SignatureHelpProvider;
import net.prominic.groovyls.providers.SuperMethodProvider;
import net.prominic.groovyls.providers.TypeHierarchyProvider;
import net.prominic.groovyls.providers.TypeDefinitionProvider;
import net.prominic.groovyls.providers.UsageItem;
import net.prominic.groovyls.providers.UsageProvider;
import net.prominic.groovyls.providers.WorkspaceSymbolProvider;
import net.prominic.groovyls.util.FileContentsTracker;
import net.prominic.groovyls.util.GradleClasspathResolver;
import net.prominic.groovyls.util.GradleProjectDetector;
import net.prominic.groovyls.util.GradleProjectInfo;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;
import net.prominic.groovyls.util.GrailsProjectDetector;
import net.prominic.groovyls.util.GrailsProjectInfo;
import net.prominic.groovyls.util.MavenProjectDetector;
import net.prominic.groovyls.util.MavenProjectInfo;
import net.prominic.groovyls.util.MavenDependencyResolver;
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
	private static final Pattern PATTERN_IMPORT_STATEMENT = Pattern
			.compile("^\\s*import\\s+(?:static\\s+)?([^\\s;]+)(?:\\s+as\\s+(\\w+))?\\s*;?\\s*$");
	private static final Pattern PATTERN_UNNECESSARY_SEMICOLON = Pattern.compile("^\\s*;\\s*$");
	private static final long DIAGNOSTIC_DEBOUNCE_MS = 250;
	private static final int DUPLICATE_CODE_MIN_LENGTH = 10;
	private static final int MAX_LINE_LENGTH = 120;

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
	private GrailsProjectInfo grailsProjectInfo;
	private List<String> userClasspathList = new ArrayList<>();
	private List<String> gradleClasspathList = Collections.emptyList();
	private List<String> gradleClasspathScopes = new ArrayList<>();
	private boolean gradleIncludeBuildscript = false;
	private List<String> mavenClasspathList = Collections.emptyList();
	private MavenProjectInfo mavenProjectInfo;
	private List<String> excludePatterns = new ArrayList<>();
	private List<String> sourceRoots = new ArrayList<>();
	private List<String> mavenRepositories = new ArrayList<>();
	private List<String> mavenDependencies = new ArrayList<>();
	private boolean classpathRecursive = false;
	private final ScheduledExecutorService compileScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "groovyls-compile");
		thread.setDaemon(true);
		return thread;
	});
	private final ScheduledExecutorService indexingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "groovyls-index");
		thread.setDaemon(true);
		return thread;
	});
	private final Object compileLock = new Object();
	private final Object compileOperationLock = new Object();
	private final Object classGraphLock = new Object();
	private final AtomicInteger gradleIndexGeneration = new AtomicInteger();
	private final AtomicInteger mavenIndexGeneration = new AtomicInteger();
	private final AtomicInteger classGraphIndexGeneration = new AtomicInteger();
	private ScheduledFuture<?> pendingCompile;
	private final Set<URI> pendingContextUris = new HashSet<>();
	private final FormattingSettings formattingSettings = new FormattingSettings();
	private final FormattingProvider formattingProvider = new FormattingProvider(fileContentsTracker,
			formattingSettings);

	public GroovyServices(ICompilationUnitFactory factory) {
		compilationUnitFactory = factory;
	}

	public void setWorkspaceRoot(Path workspaceRoot) {
		this.workspaceRoot = workspaceRoot;
		compilationUnitFactory.invalidateCompilationUnit();
		createOrUpdateCompilationUnit();
		detectGradleProject();
		detectGrailsProject();
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
		if (languageClient != null) {
			languageClient.publishDiagnostics(new PublishDiagnosticsParams(uri.toString(), new ArrayList<>()));
		}
		scheduleCompileAndVisitAST(uri);
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (!formattingSettings.isFormatOnSave() || languageClient == null) {
			return;
		}
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(params.getTextDocument().getUri());
		List<TextEdit> edits = new ArrayList<>(formattingProvider.provideDocumentFormatting(textDocument).join());
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
		detectGrailsProject();
		scheduleMavenIndexing();
		synchronized (compileOperationLock) {
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
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		if (!(params.getSettings() instanceof JsonObject)) {
			return;
		}
		JsonObject settings = (JsonObject) params.getSettings();
		this.updateConfiguration(settings);
		this.updateFormattingSettings(settings);
	}

	private void updateConfiguration(JsonObject settings) {
		List<String> prevExcludePatterns = new ArrayList<>(excludePatterns);
		List<String> prevSourceRoots = new ArrayList<>(sourceRoots);
		boolean prevClasspathRecursive = classpathRecursive;

		List<String> classpathList = new ArrayList<>();
		List<String> nextExcludePatterns = new ArrayList<>();
		List<String> nextSourceRoots = new ArrayList<>();
		List<String> nextMavenRepositories = new ArrayList<>();
		List<String> nextMavenDependencies = new ArrayList<>();
		List<String> nextGradleScopes = new ArrayList<>();
		boolean nextGradleIncludeBuildscript = false;
		boolean nextClasspathRecursive = false;

		if (settings.has("groovy") && settings.get("groovy").isJsonObject()) {
			JsonObject groovy = settings.get("groovy").getAsJsonObject();
			classpathList.addAll(readStringArray(groovy, "classpath"));
			nextExcludePatterns.addAll(readStringArray(groovy, "excludePatterns"));
			nextSourceRoots.addAll(readStringArray(groovy, "sourceRoots"));
			if (groovy.has("classpathRecursive") && groovy.get("classpathRecursive").isJsonPrimitive()) {
				nextClasspathRecursive = groovy.get("classpathRecursive").getAsBoolean();
			}
			if (groovy.has("maven") && groovy.get("maven").isJsonObject()) {
				JsonObject maven = groovy.get("maven").getAsJsonObject();
				nextMavenRepositories.addAll(readStringArray(maven, "repositories"));
				nextMavenDependencies.addAll(readStringArray(maven, "dependencies"));
			}
			if (groovy.has("gradle") && groovy.get("gradle").isJsonObject()) {
				JsonObject gradle = groovy.get("gradle").getAsJsonObject();
				nextGradleScopes.addAll(readStringArray(gradle, "classpathScopes"));
				if (gradle.has("includeBuildscript") && gradle.get("includeBuildscript").isJsonPrimitive()) {
					nextGradleIncludeBuildscript = gradle.get("includeBuildscript").getAsBoolean();
				}
			}
		}

		userClasspathList = classpathList;
		excludePatterns = nextExcludePatterns;
		sourceRoots = nextSourceRoots;
		mavenRepositories = nextMavenRepositories;
		mavenDependencies = nextMavenDependencies;
		classpathRecursive = nextClasspathRecursive;
		gradleClasspathScopes = nextGradleScopes;
		gradleIncludeBuildscript = nextGradleIncludeBuildscript;

		compilationUnitFactory.setExcludePatterns(excludePatterns);
		if (sourceRoots == null || sourceRoots.isEmpty()) {
			compilationUnitFactory.setSourceRoots(getDetectedSourceRoots());
		} else {
			compilationUnitFactory.setSourceRoots(sourceRoots);
		}
		compilationUnitFactory.setClasspathRecursive(classpathRecursive);

		applyEffectiveClasspathAsync();
		scheduleMavenIndexing();
		scheduleGradleIndexing();

		boolean structureChanged = !prevExcludePatterns.equals(excludePatterns)
				|| !prevSourceRoots.equals(sourceRoots)
				|| prevClasspathRecursive != classpathRecursive;
		if (structureChanged) {
			synchronized (compileOperationLock) {
				boolean isSameUnit = createOrUpdateCompilationUnit();
				compile();
				if (isSameUnit) {
					visitAST();
				} else {
					visitAST();
				}
				previousContext = null;
			}
		}
	}

	private void detectGrailsProject() {
		grailsProjectInfo = GrailsProjectDetector.detect(workspaceRoot);
		if ((sourceRoots == null || sourceRoots.isEmpty()) && grailsProjectInfo != null) {
			compilationUnitFactory.setSourceRoots(getDetectedSourceRoots());
			compilationUnitFactory.invalidateCompilationUnit();
			synchronized (compileOperationLock) {
				boolean isSameUnit = createOrUpdateCompilationUnit();
				compile();
				if (isSameUnit) {
					visitAST();
				} else {
					visitAST();
				}
				previousContext = null;
			}
		}
	}

	private List<String> getDetectedSourceRoots() {
		if (grailsProjectInfo == null || grailsProjectInfo.getSourceRoots().isEmpty()) {
			return Collections.emptyList();
		}
		List<String> roots = new ArrayList<>();
		for (Path root : grailsProjectInfo.getSourceRoots()) {
			if (root != null) {
				roots.add(root.toString());
			}
		}
		return roots;
	}

	private List<String> readStringArray(JsonObject parent, String key) {
		List<String> values = new ArrayList<>();
		if (parent == null || key == null) {
			return values;
		}
		if (!parent.has(key) || !parent.get(key).isJsonArray()) {
			return values;
		}
		JsonArray array = parent.get(key).getAsJsonArray();
		array.forEach(element -> values.add(element.getAsString()));
		return values;
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
		ensureClassGraphAvailable();

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
				changeEvent = new TextDocumentContentChangeEvent();
				changeEvent.setRange(new Range(position, position));
				changeEvent.setText("a()");
			} else {
				changeEvent = new TextDocumentContentChangeEvent();
				changeEvent.setRange(new Range(position, position));
				changeEvent.setText("a");
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
				TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
				changeEvent.setRange(null);
				changeEvent.setText(originalSource);
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
			TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
			changeEvent.setRange(new Range(position, position));
			changeEvent.setText(")");
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
				TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
				changeEvent.setRange(null);
				changeEvent.setText(originalSource);
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
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
			ImplementationParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		ImplementationProvider provider = new ImplementationProvider(astVisitor);
		return provider.provideImplementation(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		TypeHierarchyProvider provider = new TypeHierarchyProvider(astVisitor);
		return provider.prepareTypeHierarchy(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
		ensureAstAvailable();
		TypeHierarchyProvider provider = new TypeHierarchyProvider(astVisitor);
		return provider.provideSupertypes(params.getItem());
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
		ensureAstAvailable();
		TypeHierarchyProvider provider = new TypeHierarchyProvider(astVisitor);
		return provider.provideSubtypes(params.getItem());
	}

	@Override
	public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);
		CallHierarchyProvider provider = new CallHierarchyProvider(astVisitor);
		return provider.prepareCallHierarchy(params.getTextDocument(), params.getPosition());
	}

	@Override
	public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
			CallHierarchyIncomingCallsParams params) {
		ensureAstAvailable();
		CallHierarchyProvider provider = new CallHierarchyProvider(astVisitor);
		return provider.provideIncomingCalls(params.getItem());
	}

	@Override
	public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
			CallHierarchyOutgoingCallsParams params) {
		ensureAstAvailable();
		CallHierarchyProvider provider = new CallHierarchyProvider(astVisitor);
		return provider.provideOutgoingCalls(params.getItem());
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
		List<WorkspaceSymbol> results = new ArrayList<>(
				provider.provideWorkspaceSymbols(params.getQuery()).join());
		if (grailsProjectInfo != null && workspaceRoot != null) {
			GspTemplateSymbolProvider gspProvider = new GspTemplateSymbolProvider(workspaceRoot, grailsProjectInfo);
			results.addAll(gspProvider.provideWorkspaceSymbols(params.getQuery()));
		}
		return CompletableFuture.completedFuture(Either.forRight(results));
	}

	@Override
	public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		RenameProvider provider = new RenameProvider(astVisitor, fileContentsTracker);
		CompletableFuture<Either<Range, Range>> result = provider.providePrepareRename(params.getTextDocument(), params.getPosition());
		
		return result.thenApply(either -> {
			if (either == null) {
				return null;
			}
			// Convert Either<Range, Range> to Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>
			return Either3.forFirst(either.getLeft());
		});
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		RenameProvider provider = new RenameProvider(astVisitor, fileContentsTracker);
		return provider.provideRename(params);
	}

	@Override
	public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		FoldingRangeProvider provider = new FoldingRangeProvider(astVisitor);
		return provider.provideFoldingRanges(params.getTextDocument());
	}

	@Override
	public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		SelectionRangeProvider provider = new SelectionRangeProvider(astVisitor);
		return provider.provideSelectionRanges(params.getTextDocument(), params.getPositions());
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		URI uri = URI.create(params.getTextDocument().getUri());
		ensureCompiledForRequest(uri);

		CodeLensProvider provider = new CodeLensProvider(astVisitor);
		CompletableFuture<List<CodeLens>> result = provider.provideCodeLenses(params.getTextDocument());
		return result.thenApply(lenses -> lenses);
	}

	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		if (params == null || params.getCommand() == null) {
			return CompletableFuture.completedFuture(null);
		}
		switch (params.getCommand()) {
			case "groovy.findUsages":
				return CompletableFuture.completedFuture(handleFindUsages(params));
			case "groovy.goToSuperMethod":
				return CompletableFuture.completedFuture(handleGoToSuperMethod(params));
			default:
				return CompletableFuture.completedFuture(null);
		}
	}

	private Object handleFindUsages(ExecuteCommandParams params) {
		if (params.getArguments() == null || params.getArguments().isEmpty()) {
			return Collections.emptyList();
		}
		Object arg = params.getArguments().get(0);
		JsonObject payload = toJsonObject(arg);
		if (payload == null || !payload.has("textDocument") || !payload.has("position")) {
			return Collections.emptyList();
		}
		JsonObject textDocument = payload.getAsJsonObject("textDocument");
		JsonObject position = payload.getAsJsonObject("position");
		String uriValue = textDocument.has("uri") ? textDocument.get("uri").getAsString() : null;
		if (uriValue == null) {
			return Collections.emptyList();
		}
		int line = position.has("line") ? position.get("line").getAsInt() : 0;
		int character = position.has("character") ? position.get("character").getAsInt() : 0;
		List<String> filters = new ArrayList<>();
		if (payload.has("filters") && payload.get("filters").isJsonArray()) {
			JsonArray filterArray = payload.getAsJsonArray("filters");
			filterArray.forEach(element -> {
				if (element != null && element.isJsonPrimitive()) {
					filters.add(element.getAsString());
				}
			});
		}
		TextDocumentIdentifier doc = new TextDocumentIdentifier(uriValue);
		Position pos = new Position(line, character);
		ensureCompiledForRequest(URI.create(uriValue));
		UsageProvider provider = new UsageProvider(astVisitor);
		List<UsageItem> usages = provider.provideUsages(doc, pos, new HashSet<>(filters));
		return usages;
	}

	private Object handleGoToSuperMethod(ExecuteCommandParams params) {
		if (params.getArguments() == null || params.getArguments().isEmpty()) {
			return Collections.emptyList();
		}
		Object arg = params.getArguments().get(0);
		JsonObject payload = toJsonObject(arg);
		if (payload == null || !payload.has("textDocument") || !payload.has("position")) {
			return Collections.emptyList();
		}
		JsonObject textDocument = payload.getAsJsonObject("textDocument");
		JsonObject position = payload.getAsJsonObject("position");
		String uriValue = textDocument.has("uri") ? textDocument.get("uri").getAsString() : null;
		if (uriValue == null) {
			return Collections.emptyList();
		}
		int line = position.has("line") ? position.get("line").getAsInt() : 0;
		int character = position.has("character") ? position.get("character").getAsInt() : 0;
		TextDocumentIdentifier doc = new TextDocumentIdentifier(uriValue);
		Position pos = new Position(line, character);
		ensureCompiledForRequest(URI.create(uriValue));
		SuperMethodProvider provider = new SuperMethodProvider(astVisitor);
		return provider.provideSuperMethod(doc, pos).join();
	}

	private JsonObject toJsonObject(Object arg) {
		if (arg instanceof JsonObject) {
			return (JsonObject) arg;
		}
		if (arg instanceof Map) {
			return new com.google.gson.Gson().toJsonTree(arg).getAsJsonObject();
		}
		return null;
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
				scheduleClassGraphScan(classLoader);
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
		compileAndVisitAST(Collections.singleton(newContext));
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
		boolean hasAstForUri = astVisitor != null && !astVisitor.getNodes(contextURI).isEmpty();
		if (astVisitor == null || compilationUnit == null || shouldCompileNow || !hasAstForUri) {
			compileAndVisitAST(Collections.singleton(contextURI));
			return;
		}
		recompileIfContextChanged(contextURI);
	}

	private void ensureAstAvailable() {
		if (astVisitor != null) {
			return;
		}
		synchronized (compileOperationLock) {
			if (createOrUpdateCompilationUnit()) {
				compile();
				visitAST();
			} else if (compilationUnit != null) {
				compile();
				visitAST();
			}
		}
	}

	private void detectGradleProject() {
		scheduleGradleIndexing();
	}

	private void scheduleGradleIndexing() {
		final int generation = gradleIndexGeneration.incrementAndGet();
		indexingScheduler.execute(() -> {
			GradleProjectInfo nextProjectInfo = GradleProjectDetector.detect(workspaceRoot);
			List<String> nextClasspath = GradleClasspathResolver.resolve(nextProjectInfo, gradleClasspathScopes,
					gradleIncludeBuildscript);
			if (gradleIndexGeneration.get() != generation) {
				return;
			}
			gradleProjectInfo = nextProjectInfo;
			gradleClasspathList = nextClasspath;
			applyEffectiveClasspathAsync();
		});
	}

	private void scheduleMavenIndexing() {
		final int generation = mavenIndexGeneration.incrementAndGet();
		MavenProjectInfo detected = MavenProjectDetector.detect(workspaceRoot);
		List<String> deps = new ArrayList<>();
		List<String> repos = new ArrayList<>();
		if (detected != null) {
			deps.addAll(detected.getDependencies());
			repos.addAll(detected.getRepositories());
		}
		deps.addAll(mavenDependencies);
		repos.addAll(mavenRepositories);
		indexingScheduler.execute(() -> {
			List<String> resolved = MavenDependencyResolver.resolve(deps, repos);
			if (mavenIndexGeneration.get() != generation) {
				return;
			}
			mavenProjectInfo = detected;
			mavenClasspathList = resolved;
			applyEffectiveClasspathAsync();
		});
	}

	private void scheduleClassGraphScan(GroovyClassLoader loader) {
		final int generation = classGraphIndexGeneration.incrementAndGet();
		indexingScheduler.execute(() -> {
			ScanResult scanResult = null;
			try {
				scanResult = new ClassGraph().overrideClassLoaders(loader).enableClassInfo()
						.enableSystemJarsAndModules()
						.setMaxBufferedJarRAMSize(Integer.MAX_VALUE)
						.scan();
			} catch (ClassGraphException e) {
				scanResult = null;
			}
			if (classGraphIndexGeneration.get() != generation) {
				if (scanResult != null) {
					scanResult.close();
				}
				return;
			}
			ScanResult previous = classGraphScanResult;
			classGraphScanResult = scanResult;
			if (previous != null) {
				previous.close();
			}
		});
	}

	private void ensureClassGraphAvailable() {
		if (classGraphScanResult != null) {
			return;
		}
		synchronized (classGraphLock) {
			if (classGraphScanResult != null) {
				return;
			}
			if (classLoader != null) {
				scheduleClassGraphScan(classLoader);
			}
		}
	}

	private void applyEffectiveClasspathAsync() {
		compileScheduler.execute(this::applyEffectiveClasspath);
	}

	private void applyEffectiveClasspath() {
		synchronized (compileOperationLock) {
			List<String> merged = GradleClasspathResolver.mergeClasspath(userClasspathList, gradleClasspathList);
			merged = GradleClasspathResolver.mergeClasspath(merged, mavenClasspathList);
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
	}

	private void compileAndVisitAST(Set<URI> contextURIs) {
		if (contextURIs == null || contextURIs.isEmpty()) {
			return;
		}
		synchronized (compileOperationLock) {
			boolean hadChanges = !fileContentsTracker.getChangedURIs().isEmpty();
			boolean isSameUnit = createOrUpdateCompilationUnit();
			boolean shouldCompile = compilationUnit != null
					&& (compilationUnit.hasPendingSources() || astVisitor == null || hadChanges);
			if (shouldCompile) {
				compile();
			}
			if (grailsProjectInfo != null) {
				visitAST();
			} else if (isSameUnit && contextURIs != null && !contextURIs.isEmpty()) {
				visitAST(contextURIs);
			} else {
				visitAST();
			}
			previousContext = contextURIs.iterator().next();
		}
	}

	private void scheduleCompileAndVisitAST(URI contextURI) {
		synchronized (compileLock) {
			if (contextURI != null) {
				pendingContextUris.add(contextURI);
			}
			if (pendingCompile != null) {
				pendingCompile.cancel(false);
			}
			pendingCompile = compileScheduler.schedule(() -> {
				Set<URI> urisToCompile;
				synchronized (compileLock) {
					urisToCompile = new HashSet<>(pendingContextUris);
					pendingContextUris.clear();
					pendingCompile = null;
				}
				compileAndVisitAST(urisToCompile);
			}, DIAGNOSTIC_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void applyDidChangeAndCompileNow(DidChangeTextDocumentParams params) {
		fileContentsTracker.didChange(params);
		URI uri = URI.create(params.getTextDocument().getUri());
		compileAndVisitAST(Collections.singleton(uri));
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
		if (languageClient != null) {
			diagnostics.diagnostics.stream()
					.filter(params -> isOpenDocumentUri(URI.create(params.getUri())))
					.forEach(languageClient::publishDiagnostics);
		}
		removeFatalErrorSources(diagnostics.fatalErrorUris);
	}

	private boolean isOpenDocumentUri(URI uri) {
		if (uri == null) {
			return false;
		}
		if (fileContentsTracker.isOpen(uri)) {
			return true;
		}
		if (!"file".equalsIgnoreCase(uri.getScheme())) {
			return false;
		}
		try {
			Path diagnosticPath = Paths.get(uri).normalize();
			for (URI openUri : fileContentsTracker.getOpenURIs()) {
				if (!"file".equalsIgnoreCase(openUri.getScheme())) {
					continue;
				}
				if (diagnosticPath.equals(Paths.get(openUri).normalize())) {
					return true;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return false;
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

		ASTNodeVisitor diagnosticsVisitor = null;
		if (compilationUnit != null) {
			diagnosticsVisitor = new ASTNodeVisitor();
			diagnosticsVisitor.visitCompilationUnit(compilationUnit);
		}
		collectUndefinedVariableDiagnostics(diagnosticsByFile, diagnosticsVisitor);
		collectCodeInspectionDiagnostics(diagnosticsByFile, diagnosticsVisitor);

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

	private void collectCodeInspectionDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile,
			ASTNodeVisitor visitor) {
		if (visitor == null) {
			return;
		}
		Map<URI, List<ASTNode>> nodesByUri = new HashMap<>();
		for (ASTNode node : visitor.getNodes()) {
			URI uri = visitor.getURI(node);
			if (uri == null) {
				continue;
			}
			nodesByUri.computeIfAbsent(uri, key -> new ArrayList<>()).add(node);
		}
		for (Map.Entry<URI, List<ASTNode>> entry : nodesByUri.entrySet()) {
			URI uri = entry.getKey();
			List<ASTNode> nodes = entry.getValue();
			collectUnusedImportDiagnostics(diagnosticsByFile, visitor, uri, nodes);
			collectRedundantCastDiagnostics(diagnosticsByFile, uri, nodes);
			collectUnnecessarySemicolonDiagnostics(diagnosticsByFile, uri, nodes);
			collectEmptyBlockDiagnostics(diagnosticsByFile, visitor, uri, nodes);
			collectDuplicateCodeDiagnostics(diagnosticsByFile, uri, nodes);
			collectCodeStyleDiagnostics(diagnosticsByFile, uri);
			collectBestPracticeDiagnostics(diagnosticsByFile, uri, nodes);
		}
	}

	private void collectUnusedImportDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, ASTNodeVisitor visitor,
			URI uri, List<ASTNode> nodes) {
		Set<String> referencedNames = collectReferencedNames(nodes);
		boolean hasImportNodes = nodes.stream().anyMatch(ImportNode.class::isInstance);
		if (!hasImportNodes) {
			collectUnusedImportDiagnosticsFromSource(diagnosticsByFile, uri, referencedNames);
			return;
		}
		for (ASTNode node : nodes) {
			if (!(node instanceof ImportNode)) {
				continue;
			}
			ImportNode importNode = (ImportNode) node;
			if (importNode.isStar()) {
				continue;
			}
			String importName = getImportReferenceName(importNode);
			if (importName == null || importName.isBlank()) {
				continue;
			}
			if (!referencedNames.contains(importName)) {
				Range range = GroovyLanguageServerUtils.astNodeToRange(importNode);
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Unused import: " + importName);
			}
		}
	}

	private void collectUnusedImportDiagnosticsFromSource(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri,
			Set<String> referencedNames) {
		String source = fileContentsTracker.getContents(uri);
		if (source == null || source.isBlank()) {
			return;
		}
		String[] lines = source.split("\\r?\\n", -1);
		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			String line = lines[lineIndex];
			Matcher matcher = PATTERN_IMPORT_STATEMENT.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String importPath = matcher.group(1);
			if (importPath == null || importPath.isBlank() || importPath.endsWith(".*")) {
				continue;
			}
			String alias = matcher.group(2);
			String importName = alias != null && !alias.isBlank() ? alias : getSimpleImportName(importPath);
			if (importName == null || importName.isBlank()) {
				continue;
			}
			if (!referencedNames.contains(importName)) {
				Range range = new Range(new Position(lineIndex, 0), new Position(lineIndex, line.length()));
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Unused import: " + importName);
			}
		}
	}

	private String getSimpleImportName(String importPath) {
		int lastDot = importPath.lastIndexOf('.');
		String name = lastDot >= 0 ? importPath.substring(lastDot + 1) : importPath;
		return name != null && !name.isBlank() ? name : null;
	}

	private Set<String> collectReferencedNames(List<ASTNode> nodes) {
		Set<String> referencedNames = new HashSet<>();
		for (ASTNode node : nodes) {
			if (node instanceof ClassExpression) {
				addClassNodeReference(((ClassExpression) node).getType(), referencedNames);
			} else if (node instanceof ConstructorCallExpression) {
				addClassNodeReference(((ConstructorCallExpression) node).getType(), referencedNames);
			} else if (node instanceof VariableExpression) {
				String name = ((VariableExpression) node).getName();
				if (name != null && !name.isBlank()) {
					referencedNames.add(name);
				}
			} else if (node instanceof PropertyExpression) {
				String name = ((PropertyExpression) node).getPropertyAsString();
				if (name != null && !name.isBlank()) {
					referencedNames.add(name);
				}
			} else if (node instanceof MethodCallExpression) {
				String name = ((MethodCallExpression) node).getMethodAsString();
				if (name != null && !name.isBlank()) {
					referencedNames.add(name);
				}
			} else if (node instanceof StaticMethodCallExpression) {
				StaticMethodCallExpression staticCall = (StaticMethodCallExpression) node;
				String name = staticCall.getMethod();
				if (name != null && !name.isBlank()) {
					referencedNames.add(name);
				}
				addClassNodeReference(staticCall.getOwnerType(), referencedNames);
			} else if (node instanceof FieldNode) {
				addClassNodeReference(((FieldNode) node).getType(), referencedNames);
			} else if (node instanceof PropertyNode) {
				addClassNodeReference(((PropertyNode) node).getType(), referencedNames);
			} else if (node instanceof MethodNode) {
				addClassNodeReference(((MethodNode) node).getReturnType(), referencedNames);
			} else if (node instanceof Parameter) {
				addClassNodeReference(((Parameter) node).getType(), referencedNames);
			} else if (node instanceof ClassNode) {
				addClassNodeReference((ClassNode) node, referencedNames);
			}
		}
		return referencedNames;
	}

	private void addClassNodeReference(ClassNode classNode, Set<String> referencedNames) {
		if (classNode == null) {
			return;
		}
		String name = classNode.getName();
		if (name != null && !name.isBlank()) {
			referencedNames.add(name);
		}
		String simpleName = classNode.getNameWithoutPackage();
		if (simpleName != null && !simpleName.isBlank()) {
			referencedNames.add(simpleName);
		}
	}

	private String getImportReferenceName(ImportNode importNode) {
		String alias = importNode.getAlias();
		if (alias != null && !alias.isBlank()) {
			return alias;
		}
		String fieldName = importNode.getFieldName();
		if (fieldName != null && !fieldName.isBlank()) {
			return fieldName;
		}
		ClassNode type = importNode.getType();
		if (type != null) {
			String simpleName = type.getNameWithoutPackage();
			if (simpleName != null && !simpleName.isBlank()) {
				return simpleName;
			}
			return type.getName();
		}
		String className = importNode.getClassName();
		if (className != null && !className.isBlank()) {
			int lastDot = className.lastIndexOf('.');
			return lastDot >= 0 ? className.substring(lastDot + 1) : className;
		}
		return null;
	}

	private void collectRedundantCastDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri,
			List<ASTNode> nodes) {
		for (ASTNode node : nodes) {
			if (!(node instanceof CastExpression)) {
				continue;
			}
			CastExpression castExpression = (CastExpression) node;
			ClassNode castType = castExpression.getType();
			Expression expr = castExpression.getExpression();
			ClassNode exprType = expr != null ? expr.getType() : null;
			if (castType == null || exprType == null) {
				continue;
			}
			ClassNode castRedirect = castType.redirect();
			ClassNode exprRedirect = exprType.redirect();
			if (castRedirect != null && castRedirect.equals(exprRedirect)) {
				Range range = GroovyLanguageServerUtils.astNodeToRange(castExpression);
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Redundant cast");
			}
		}
	}

	private void collectUnnecessarySemicolonDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri,
			List<ASTNode> nodes) {
		boolean foundSemicolon = false;
		for (ASTNode node : nodes) {
			if (!(node instanceof EmptyStatement)) {
				continue;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(node);
			addWarningDiagnostic(diagnosticsByFile, uri, range, "Unnecessary semicolon");
			if (range != null) {
				foundSemicolon = true;
			}
		}
		if (!foundSemicolon) {
			collectUnnecessarySemicolonDiagnosticsFromSource(diagnosticsByFile, uri);
		}
	}

	private void collectUnnecessarySemicolonDiagnosticsFromSource(Map<URI, List<Diagnostic>> diagnosticsByFile,
			URI uri) {
		String source = fileContentsTracker.getContents(uri);
		if (source == null || source.isBlank()) {
			return;
		}
		String[] lines = source.split("\\r?\\n", -1);
		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			String line = lines[lineIndex];
			if (!PATTERN_UNNECESSARY_SEMICOLON.matcher(line).matches()) {
				continue;
			}
			int col = line.indexOf(';');
			if (col < 0) {
				continue;
			}
			Range range = new Range(new Position(lineIndex, col), new Position(lineIndex, col + 1));
			addWarningDiagnostic(diagnosticsByFile, uri, range, "Unnecessary semicolon");
		}
	}

	private void collectEmptyBlockDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, ASTNodeVisitor visitor,
			URI uri, List<ASTNode> nodes) {
		for (ASTNode node : nodes) {
			if (!(node instanceof BlockStatement)) {
				continue;
			}
			BlockStatement block = (BlockStatement) node;
			if (block.getStatements() != null && !block.getStatements().isEmpty()) {
				continue;
			}
			ASTNode parent = visitor.getParent(block);
			if (parent instanceof ModuleNode) {
				continue;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(block);
			addWarningDiagnostic(diagnosticsByFile, uri, range, "Empty block");
		}
	}

	private void collectDuplicateCodeDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri,
			List<ASTNode> nodes) {
		String source = fileContentsTracker.getContents(uri);
		if (source == null || source.isBlank()) {
			return;
		}
		Map<String, List<ASTNode>> blocksByText = new HashMap<>();
		for (ASTNode node : nodes) {
			if (!(node instanceof MethodNode) && !(node instanceof ClosureExpression)
					&& !(node instanceof BlockStatement)) {
				continue;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(node);
			String text = getSourceText(source, range);
			if (text == null) {
				continue;
			}
			String trimmed = text.trim();
			if (trimmed.length() <= DUPLICATE_CODE_MIN_LENGTH) {
				continue;
			}
			blocksByText.computeIfAbsent(trimmed, key -> new ArrayList<>()).add(node);
		}
		for (List<ASTNode> duplicates : blocksByText.values()) {
			if (duplicates.size() < 2) {
				continue;
			}
			for (ASTNode node : duplicates) {
				Range range = GroovyLanguageServerUtils.astNodeToRange(node);
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Duplicate code detected");
			}
		}
	}

	private void collectCodeStyleDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri) {
		String source = fileContentsTracker.getContents(uri);
		if (source == null) {
			return;
		}
		String[] lines = source.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			int length = line.length();
			if (length > MAX_LINE_LENGTH) {
				Range range = new Range(new Position(i, MAX_LINE_LENGTH), new Position(i, length));
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Line exceeds 120 characters");
			}
			int trailingStart = length;
			while (trailingStart > 0 && Character.isWhitespace(line.charAt(trailingStart - 1))) {
				trailingStart--;
			}
			if (trailingStart < length) {
				Range range = new Range(new Position(i, trailingStart), new Position(i, length));
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Trailing whitespace");
			}
		}
	}

	private void collectBestPracticeDiagnostics(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri,
			List<ASTNode> nodes) {
		for (ASTNode node : nodes) {
			if (!(node instanceof BinaryExpression)) {
				continue;
			}
			BinaryExpression binaryExpression = (BinaryExpression) node;
			String operation = binaryExpression.getOperation() != null
					? binaryExpression.getOperation().getText()
					: null;
			if (!"==".equals(operation)) {
				continue;
			}
			if (isBooleanConstant(binaryExpression.getLeftExpression())
					|| isBooleanConstant(binaryExpression.getRightExpression())) {
				Range range = GroovyLanguageServerUtils.astNodeToRange(binaryExpression);
				addWarningDiagnostic(diagnosticsByFile, uri, range, "Simplify boolean comparison");
			}
		}
	}

	private boolean isBooleanConstant(Expression expr) {
		if (!(expr instanceof ConstantExpression)) {
			return false;
		}
		Object value = ((ConstantExpression) expr).getValue();
		return value instanceof Boolean;
	}

	private String getSourceText(String source, Range range) {
		if (range == null) {
			return null;
		}
		int startOffset = Positions.getOffset(source, range.getStart());
		int endOffset = Positions.getOffset(source, range.getEnd());
		if (startOffset < 0 || endOffset < 0 || endOffset < startOffset) {
			return null;
		}
		int safeEnd = Math.min(source.length(), endOffset);
		if (safeEnd <= startOffset) {
			return null;
		}
		return source.substring(startOffset, safeEnd);
	}

	private void addWarningDiagnostic(Map<URI, List<Diagnostic>> diagnosticsByFile, URI uri, Range range,
			String message) {
		if (uri == null || range == null) {
			return;
		}
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setRange(range);
		diagnostic.setSeverity(DiagnosticSeverity.Warning);
		diagnostic.setMessage(message);
		diagnosticsByFile.computeIfAbsent(uri, key -> new ArrayList<>()).add(diagnostic);
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
