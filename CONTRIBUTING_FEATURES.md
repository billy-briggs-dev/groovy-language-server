# Contributing Missing Features - Quick Start Guide

Want to add a missing LSP feature to the Groovy Language Server? This guide will help you get started.

---

## Prerequisites

- Java 21+
- Gradle (wrapper included)
- Understanding of LSP basics
- Familiarity with Groovy or Java

---

## Quick Start

### 1. Setup Development Environment

```bash
# Clone the repository
git clone https://github.com/billy-briggs-dev/groovy-language-server.git
cd groovy-language-server

# Build the project
./gradlew build

# Run tests
./gradlew test
```

### 2. Choose a Feature to Implement

Start with **easy wins** for your first contribution:

#### 🟢 Easy (Good First Issues)
- **Folding Ranges** - Return AST node ranges for collapsible code blocks
- **Selection Range** - Return nested AST ranges for smart selection
- **Prepare Rename** - Validate symbols before renaming
- **Document Links** - Parse and return clickable URLs in comments

#### 🟡 Medium (Good Learning Projects)
- **Code Lens** - Show reference counts above symbols
- **Basic Code Actions** - Add/remove imports
- **Inlay Hints** - Show parameter names inline

#### 🔴 Hard (Significant Features)
- **Semantic Tokens** - Syntax highlighting with type information
- **Enhanced Diagnostics** - Detect unused declarations
- **Advanced Code Actions** - Extract method, inline variable

### 3. Understand the Architecture

```
src/main/java/net/prominic/groovyls/
├── GroovyLanguageServer.java   # LSP entry point, sets capabilities
├── GroovyServices.java          # Core service, coordinates providers
├── providers/                   # Feature implementations
│   ├── CompletionProvider.java
│   ├── HoverProvider.java
│   ├── DefinitionProvider.java
│   └── ... (add new providers here)
├── compiler/                    # AST utilities
└── util/                        # Helper classes
```

---

## Implementation Pattern

### Step 1: Create a Provider

Example: Adding Code Lens support

```java
// src/main/java/net/prominic/groovyls/providers/CodeLensProvider.java
package net.prominic.groovyls.providers;

import org.eclipse.lsp4j.*;
import java.util.*;

public class CodeLensProvider {
    
    public List<CodeLens> provideCodeLens(URI uri) {
        List<CodeLens> lenses = new ArrayList<>();
        
        // TODO: Walk AST and create code lenses
        // Example: Show reference counts above methods
        
        return lenses;
    }
}
```

### Step 2: Register in GroovyLanguageServer

```java
// In GroovyLanguageServer.java, initialize() method

ServerCapabilities capabilities = new ServerCapabilities();
// ... existing capabilities ...

// Add new capability
capabilities.setCodeLensProvider(new CodeLensOptions(false)); // false = no resolveProvider
```

### Step 3: Wire to GroovyServices

```java
// In GroovyServices.java

private CodeLensProvider codeLensProvider;

// In constructor
this.codeLensProvider = new CodeLensProvider();

// Add handler method
public CompletableFuture<List<CodeLens>> codeLens(CodeLensParams params) {
    URI uri = URI.create(params.getTextDocument().getUri());
    return CompletableFuture.supplyAsync(() -> {
        return codeLensProvider.provideCodeLens(uri);
    });
}
```

### Step 4: Implement in TextDocumentService

```java
// GroovyServices implements TextDocumentService

@Override
public CompletableFuture<List<CodeLens>> codeLens(CodeLensParams params) {
    return codeLens(params); // calls our method above
}
```

### Step 5: Write Tests

```java
// src/test/java/net/prominic/groovyls/GroovyServicesCodeLensTests.java
package net.prominic.groovyls;

import org.junit.jupiter.api.Test;
import org.eclipse.lsp4j.*;

class GroovyServicesCodeLensTests extends GroovyServicesTestBase {
    
    @Test
    void testCodeLensForMethod() throws Exception {
        // Given: A Groovy file with a method
        String groovyCode = """
            class Example {
                def myMethod() {
                    println "Hello"
                }
            }
        """;
        
        // When: Request code lens
        Path filePath = newTempFile("Example.groovy", groovyCode);
        CodeLensParams params = new CodeLensParams(
            new TextDocumentIdentifier(filePath.toUri().toString())
        );
        List<CodeLens> lenses = services.codeLens(params).get();
        
        // Then: Expect lens above method
        assertThat(lenses).isNotEmpty();
        assertThat(lenses.get(0).getCommand().getTitle()).contains("references");
    }
}
```

---

## Example: Implementing Folding Ranges

### What are Folding Ranges?

Folding ranges tell the editor which code blocks can be collapsed:
- Methods
- Classes
- Closures
- Imports
- Comments

### Implementation Steps

#### 1. Create the Provider

```java
// src/main/java/net/prominic/groovyls/providers/FoldingRangeProvider.java
package net.prominic.groovyls.providers;

import org.codehaus.groovy.ast.*;
import org.eclipse.lsp4j.*;
import java.util.*;

public class FoldingRangeProvider {
    
    public List<FoldingRange> provideFoldingRanges(ModuleNode moduleNode) {
        List<FoldingRange> ranges = new ArrayList<>();
        
        // Fold imports
        if (!moduleNode.getImports().isEmpty()) {
            ImportNode first = moduleNode.getImports().get(0);
            ImportNode last = moduleNode.getImports().get(moduleNode.getImports().size() - 1);
            
            FoldingRange range = new FoldingRange(
                first.getLineNumber() - 1,
                last.getLineNumber() - 1
            );
            range.setKind(FoldingRangeKind.Imports);
            ranges.add(range);
        }
        
        // Fold classes
        for (ClassNode classNode : moduleNode.getClasses()) {
            ranges.addAll(foldClass(classNode));
        }
        
        return ranges;
    }
    
    private List<FoldingRange> foldClass(ClassNode classNode) {
        List<FoldingRange> ranges = new ArrayList<>();
        
        // Fold entire class
        FoldingRange classRange = new FoldingRange(
            classNode.getLineNumber() - 1,
            classNode.getLastLineNumber() - 1
        );
        classRange.setKind(FoldingRangeKind.Region);
        ranges.add(classRange);
        
        // Fold methods
        for (MethodNode method : classNode.getMethods()) {
            FoldingRange methodRange = new FoldingRange(
                method.getLineNumber() - 1,
                method.getLastLineNumber() - 1
            );
            methodRange.setKind(FoldingRangeKind.Region);
            ranges.add(methodRange);
        }
        
        return ranges;
    }
}
```

#### 2. Register the Capability

```java
// In GroovyLanguageServer.java
capabilities.setFoldingRangeProvider(true);
```

#### 3. Add Handler

```java
// In GroovyServices.java
private FoldingRangeProvider foldingRangeProvider;

public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    return CompletableFuture.supplyAsync(() -> {
        URI uri = URI.create(params.getTextDocument().getUri());
        ModuleNode moduleNode = getModuleNode(uri);
        return foldingRangeProvider.provideFoldingRanges(moduleNode);
    });
}
```

#### 4. Test It

```java
@Test
void testFoldingRangeForClass() throws Exception {
    String code = """
        class Example {
            def method1() { }
            def method2() { }
        }
    """;
    
    Path path = newTempFile("Example.groovy", code);
    FoldingRangeRequestParams params = new FoldingRangeRequestParams(
        new TextDocumentIdentifier(path.toUri().toString())
    );
    
    List<FoldingRange> ranges = services.foldingRange(params).get();
    
    assertThat(ranges).hasSize(3); // class + 2 methods
}
```

---

## Testing Your Changes

### Run Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests GroovyServicesCodeLensTests

# Run specific test method
./gradlew test --tests GroovyServicesCodeLensTests.testCodeLensForMethod
```

### Test with VS Code

```bash
# Build the JAR
./gradlew build

# The JAR is in build/libs/groovy-language-server-all.jar

# Copy to VS Code extension (if you have it)
cp build/libs/groovy-language-server-all.jar vscode-extension/lib/

# Or test with any LSP client
java -jar build/libs/groovy-language-server-all.jar
```

### Manual Testing with LSP Inspector

You can use tools like:
- [LSP Inspector](https://github.com/microsoft/language-server-protocol-inspector)
- [VSCode Language Server Extension Guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide)

---

## Common Pitfalls

### 1. Line Number Indexing
- **Groovy AST:** 1-based line numbers
- **LSP:** 0-based line numbers
- **Always subtract 1:** `astNode.getLineNumber() - 1`

### 2. Null Handling
- Always check for null before accessing AST nodes
- Use Optional or defensive programming

### 3. Thread Safety
- Most handlers return `CompletableFuture`
- Use `CompletableFuture.supplyAsync()` for background work
- Don't block the main thread

### 4. URI Handling
- Convert strings to URI: `URI.create(params.getTextDocument().getUri())`
- Always use URI, not file paths directly

---

## Resources

### Documentation
- [LSP Specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)
- [LSP4J Documentation](https://github.com/eclipse-lsp4j/lsp4j)
- [Groovy AST Guide](https://groovy-lang.org/metaprogramming.html#_compilation_phases_guide)

### Analysis Documents (in this repo)
- [ANALYSIS_SUMMARY.md](ANALYSIS_SUMMARY.md) - Executive summary
- [LSP_FEATURE_CHECKLIST.md](LSP_FEATURE_CHECKLIST.md) - Feature status
- [MISSING_FEATURES_ANALYSIS.md](MISSING_FEATURES_ANALYSIS.md) - Detailed analysis

### Code Examples
- Look at existing providers in `src/main/java/net/prominic/groovyls/providers/`
- Tests in `src/test/java/net/prominic/groovyls/`

---

## Getting Help

1. **Read existing code** - See how similar features are implemented
2. **Check tests** - Tests show expected behavior
3. **Open an issue** - Ask questions on GitHub
4. **Join discussions** - Engage with maintainers

---

## Submission Checklist

Before submitting a PR:

- [ ] Feature is implemented and working
- [ ] Unit tests added and passing
- [ ] Manually tested with VS Code or other client
- [ ] Code follows existing style
- [ ] No compiler warnings
- [ ] Documentation updated (if needed)
- [ ] PR description explains what and why

---

## Example PRs to Study

Look at these areas for examples:
- `CompletionProvider.java` - Shows complex provider logic
- `TypeHierarchyProvider.java` - Shows AST traversal
- `ReferenceProvider.java` - Shows workspace-wide search
- `FormattingProvider.java` - Shows code generation

---

## Quick Reference: LSP Methods to Implement

Pick one and follow the pattern above:

```java
// Folding Ranges
@Override
public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params)

// Code Lens
@Override
public CompletableFuture<List<CodeLens>> codeLens(CodeLensParams params)

// Code Actions
@Override
public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)

// Semantic Tokens
@Override
public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params)

// Inlay Hints
@Override
public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params)

// Selection Range
@Override
public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params)
```

---

## Ready to Contribute?

1. Pick a feature from [LSP_FEATURE_CHECKLIST.md](LSP_FEATURE_CHECKLIST.md)
2. Follow the implementation pattern above
3. Write tests
4. Submit a PR!

Good luck! 🚀

---

**Questions?** Open an issue or discussion on GitHub.
