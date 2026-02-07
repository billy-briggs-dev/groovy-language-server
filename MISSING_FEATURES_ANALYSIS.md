# Missing Features Analysis: Groovy Language Server

**Generated:** 2026-02-07  
**Repository:** billy-briggs-dev/groovy-language-server  
**Purpose:** Comprehensive analysis of missing LSP features compared to the Language Server Protocol specification and IntelliJ IDEA

---

## Executive Summary

The Groovy Language Server currently implements **approximately 45-50% of the LSP specification**, with strong coverage in:
- ✅ Navigation features (go-to-definition, find references, type hierarchy)
- ✅ Basic code intelligence (completion, hover, signature help)
- ✅ Basic formatting support
- ✅ Some diagnostic capabilities (syntax errors, undefined variables)

**Key gaps** exist in:
- ❌ Code Actions (quick fixes, refactorings)
- ❌ Advanced refactoring tools
- ❌ Code Lens (reference counts, implementations)
- ❌ Semantic Tokens (syntax highlighting)
- ❌ Folding Ranges (code folding)
- ❌ Inlay Hints (parameter names, type hints)
- ❌ Advanced diagnostics and linting
- ❌ Testing framework integration
- ❌ Pull diagnostics model

---

## Part 1: Currently Implemented LSP Features

### ✅ Text Document Capabilities

| LSP Feature | Status | Provider/Handler | Notes |
|------------|--------|------------------|-------|
| **textDocument/completion** | ✅ Implemented | `CompletionProvider` | Supports keywords, members, trigger chars: `.`, `(`, `,` |
| **textDocument/hover** | ✅ Implemented | `HoverProvider` | Shows documentation and type info |
| **textDocument/definition** | ✅ Implemented | `DefinitionProvider` | Go to definition support |
| **textDocument/typeDefinition** | ✅ Implemented | `TypeDefinitionProvider` | Go to type definition |
| **textDocument/implementation** | ✅ Implemented | `ImplementationProvider` | Find implementations |
| **textDocument/references** | ✅ Implemented | `ReferenceProvider` | Find all references |
| **textDocument/documentSymbol** | ✅ Implemented | `DocumentSymbolProvider` | Document outline |
| **textDocument/signatureHelp** | ✅ Implemented | `SignatureHelpProvider` | Function signatures, triggers: `(`, `,` |
| **textDocument/formatting** | ✅ Implemented | `FormattingProvider` | Document formatting |
| **textDocument/rangeFormatting** | ✅ Implemented | `FormattingProvider` | Range formatting |
| **textDocument/rename** | ✅ Implemented | `RenameProvider` | Symbol renaming |
| **textDocument/diagnostic** | ⚠️ Partial | Built into `GroovyServices` | Push diagnostics only, syntax errors + some semantic |

### ✅ Workspace Capabilities

| LSP Feature | Status | Handler | Notes |
|------------|--------|---------|-------|
| **workspace/symbol** | ✅ Implemented | `WorkspaceSymbolProvider` | Workspace-wide symbol search |
| **workspace/didChangeConfiguration** | ✅ Implemented | `GroovyServices` | Configuration updates |
| **workspace/didChangeWatchedFiles** | ✅ Implemented | `GroovyServices` | File change notifications |

### ✅ Advanced Navigation

| LSP Feature | Status | Provider | Notes |
|------------|--------|----------|-------|
| **typeHierarchy/prepare** | ✅ Implemented | `TypeHierarchyProvider` | Type hierarchy support |
| **typeHierarchy/supertypes** | ✅ Implemented | `TypeHierarchyProvider` | Navigate to supertypes |
| **typeHierarchy/subtypes** | ✅ Implemented | `TypeHierarchyProvider` | Navigate to subtypes |
| **callHierarchy/prepare** | ✅ Implemented | `CallHierarchyProvider` | Call hierarchy support |
| **callHierarchy/incomingCalls** | ✅ Implemented | `CallHierarchyProvider` | Incoming calls |
| **callHierarchy/outgoingCalls** | ✅ Implemented | `CallHierarchyProvider` | Outgoing calls |

### ✅ Custom Commands

| Command | Status | Handler | Notes |
|---------|--------|---------|-------|
| **groovy.findUsages** | ✅ Implemented | `UsageProvider` | Find all usages |
| **groovy.goToSuperMethod** | ✅ Implemented | `SuperMethodProvider` | Navigate to super method |

### ✅ Groovy-Specific Features

- ✅ **MetaClass method detection** - Dynamic method resolution
- ✅ **AST Transformation support** - `@Delegate`, `@Builder`, `@Canonical`, etc.
- ✅ **Closure delegate resolution** - `@DelegatesTo` support
- ✅ **GString type inference** - Interpolated string support
- ✅ **Gradle project detection** - Auto-detect and configure classpath
- ✅ **Maven project detection** - Parse pom.xml dependencies
- ✅ **Grails 7.0+ support** - Domain classes, GORM, GSP templates
- ✅ **Multi-module projects** - Gradle/Maven multi-module support

---

## Part 2: Missing LSP Features (High Priority)

These are **standard LSP features** that should be implemented for feature completeness:

### ❌ Code Actions

**Status:** Not Implemented  
**Impact:** High - Users cannot quick-fix errors or perform common refactorings  
**LSP Methods:**
- `textDocument/codeAction` - Not implemented
- `codeAction/resolve` - Not implemented

**What's Missing:**
- Quick fixes for common errors
- Import organization (add missing import, remove unused imports)
- Generate boilerplate (getters/setters, constructors, toString, equals/hashCode)
- Extract method/variable/constant
- Inline variable/method
- Convert between types (e.g., if-else to ternary)
- Surround with try-catch, if-else
- Optimize imports
- Fix all in file/scope

**Difficulty:** Medium-High  
**Recommendation:** Start with simple fixes (add import, remove unused import) and expand incrementally.

---

### ❌ Code Lens

**Status:** Not Implemented  
**Impact:** Medium - Missing visual indicators for references and implementations  
**LSP Methods:**
- `textDocument/codeLens` - Not implemented
- `codeLens/resolve` - Not implemented

**What's Missing:**
- Show reference counts above methods/classes ("5 references", "3 implementations")
- Run test buttons for test methods
- Override/implement indicators
- Show usages inline
- Quick actions (debug, profile, etc.)

**Difficulty:** Low-Medium  
**Recommendation:** Start with reference counts, as UsageProvider already tracks this data.

---

### ❌ Semantic Tokens (Syntax Highlighting)

**Status:** Not Implemented  
**Impact:** Medium - Editors rely on generic syntax highlighting  
**LSP Methods:**
- `textDocument/semanticTokens/full` - Not implemented
- `textDocument/semanticTokens/full/delta` - Not implemented
- `textDocument/semanticTokens/range` - Not implemented

**What's Missing:**
- Semantic-aware syntax coloring (distinguish local vars vs fields vs parameters)
- Highlight unused variables/imports
- Differentiate between static/instance members
- Show dynamic vs statically typed variables
- Custom colors for annotations, AST transformations

**Difficulty:** Medium  
**Recommendation:** Implement full semantic tokens first, then add delta support for performance.

---

### ❌ Folding Ranges (Code Folding)

**Status:** ✅ Now Implemented  
**Impact:** Medium - Users can collapse code blocks  
**LSP Methods:**
- `textDocument/foldingRange` - ✅ Implemented

**What it provides:**
- Fold methods, classes, closures
- Fold imports section
- Fold blocks (multi-line constructs)

**Implementation:** Walks AST and returns ranges for foldable constructs (classes, methods, closures, imports).

---

### ❌ Inlay Hints

**Status:** Not Implemented  
**Impact:** Medium - Missing inline parameter names and type hints  
**LSP Methods:**
- `textDocument/inlayHint` - Not implemented
- `inlayHint/resolve` - Not implemented

**What's Missing:**
- Parameter names in method calls: `foo(name: "John", age: 30)`
- Inferred types for `def` variables: `def name: String = "John"`
- Return type hints for methods
- Chain call hints

**Difficulty:** Medium  
**Recommendation:** Start with parameter name hints for method calls.

---

### ❌ Selection Range (Smart Selection)

**Status:** ✅ Now Implemented  
**Impact:** Low-Medium - Users can expand selection semantically  
**LSP Methods:**
- `textDocument/selectionRange` - ✅ Implemented

**What it provides:**
- Expand selection to expression, statement, block, method, class
- Semantic-aware selection expansion
- Nested selection ranges from innermost to outermost AST nodes

**Implementation:** Walks AST to find all enclosing nodes at a position and returns nested selection ranges.

---

### ❌ Document Link

**Status:** Not Implemented  
**Impact:** Low - Cannot navigate to URLs or file paths in comments/strings  
**LSP Methods:**
- `textDocument/documentLink` - Not implemented
- `documentLink/resolve` - Not implemented

**What's Missing:**
- Clickable URLs in comments
- Navigate to imported files
- Navigate to external documentation

**Difficulty:** Low  
**Recommendation:** Parse comments and strings for URLs/paths.

---

### ❌ Document Color

**Status:** Not Implemented  
**Impact:** Very Low - Cannot visualize colors in code  
**LSP Methods:**
- `textDocument/documentColor` - Not implemented
- `textDocument/colorPresentation` - Not implemented

**What's Missing:**
- Color picker for hex colors (#RRGGBB)
- Color preview for CSS/UI code

**Difficulty:** Very Low  
**Recommendation:** Low priority unless working with UI code.

---

### ❌ Linked Editing Range

**Status:** Not Implemented  
**Impact:** Low - Cannot edit multiple locations simultaneously  
**LSP Methods:**
- `textDocument/linkedEditingRange` - Not implemented

**What's Missing:**
- Simultaneous editing of opening/closing tags (for XML/HTML in GSP)
- Simultaneous editing of start/end markers

**Difficulty:** Low  
**Recommendation:** Useful for GSP templates.

---

### ❌ Prepare Rename

**Status:** ✅ Now Implemented  
**Impact:** Low - Validates rename before execution  
**LSP Methods:**
- `textDocument/prepareRename` - ✅ Implemented

**What it provides:**
- Validate that symbol can be renamed
- Show preview of what will be renamed
- Reject invalid rename locations (e.g., keywords, non-renameable symbols)

**Implementation:** Checks if node at position is renameable (classes, methods, properties, variables) and returns the range.

---

### ❌ Pull Diagnostics

**Status:** Not Implemented (Push diagnostics only)  
**Impact:** Low - Limited control over when diagnostics are computed  
**LSP Methods:**
- `textDocument/diagnostic` - Not implemented (pull model)
- `workspace/diagnostic` - Not implemented

**What's Missing:**
- Client-controlled diagnostic timing
- Workspace-wide diagnostics on demand

**Difficulty:** Medium  
**Recommendation:** Current push model is sufficient for most use cases.

---

## Part 3: Missing Advanced Features (Medium Priority)

These features go beyond basic LSP and provide enhanced IDE-like functionality:

### 🟡 Advanced Refactoring

**Status:** Only rename is implemented  
**Impact:** High for developers  

**What's Missing:**
- Extract method/variable/constant
- Inline variable/method
- Move class to another package
- Change method signature
- Safe delete (with usage check)
- Convert between Java/Groovy idioms
- Extract interface/superclass
- Pull up/push down members

**Difficulty:** High  
**Recommendation:** Start with extract method/variable.

---

### 🟡 Advanced Code Completion

**Status:** Basic completion implemented  
**Impact:** Medium  

**What's Missing:**
- **Smart type-aware completion** - Filter suggestions by expected type
- **Post-fix completion** - `.for`, `.if`, `.null`, `.nn` (not null)
- **Import auto-completion** - Suggest and auto-import on selection
- **Chain completion** - Complete entire call chains
- **Template expansion** - Expand snippets/live templates
- **Context-aware suggestions** - Different suggestions based on location
- **Fuzzy matching** - Better ranking of results
- **Documentation preview** - Show docs while browsing completions

**Difficulty:** Medium-High  
**Recommendation:** Implement smart type filtering first.

---

### 🟡 Enhanced Diagnostics

**Status:** Basic syntax errors + some semantic analysis  
**Impact:** High  

**What's Missing:**
- **Unused declarations** - Unused variables, methods, imports, classes
- **Type mismatches** - Incompatible types in assignments
- **Null safety** - Potential null pointer warnings
- **Resource leaks** - Unclosed streams/connections
- **Unreachable code** - Dead code detection
- **Code style violations** - Naming conventions, complexity
- **Deprecated API usage** - Warn about deprecated methods
- **Security vulnerabilities** - SQL injection, XSS, etc.
- **Performance warnings** - Inefficient patterns
- **Groovy-specific warnings** - GString usage, closure scope issues

**Difficulty:** Medium-High  
**Recommendation:** Add unused declaration detection first.

---

### 🟡 Testing Integration

**Status:** Not implemented  
**Impact:** High for TDD workflows  

**What's Missing:**
- **Spock framework support** - Recognize Spock specifications
- **JUnit/TestNG integration** - Detect test methods
- **Run tests from editor** - Execute individual tests
- **Test results inline** - Show pass/fail in editor
- **Test coverage** - Highlight covered/uncovered code
- **Generate test stubs** - Create test skeletons
- **Navigate test ↔ source** - Jump between test and implementation

**Difficulty:** Medium-High  
**Recommendation:** Start with Spock test detection and code lens for "Run Test".

---

### 🟡 DSL Support Enhancement

**Status:** Basic `@DelegatesTo` support  
**Impact:** Medium for Gradle/Jenkins users  

**What's Missing:**
- **Gradle DSL completion** - Full `dependencies {}`, `plugins {}` support
- **Jenkins Pipeline DSL** - `node`, `stage`, `sh` completion
- **Custom DSL recognition** - `@DslMarker` annotation support
- **Builder pattern detection** - Auto-complete DSL methods
- **Context-specific completion** - Different suggestions inside DSL blocks

**Difficulty:** Medium-High  
**Recommendation:** Focus on Gradle DSL as it's most common.

---

### 🟡 Documentation Enhancements

**Status:** Basic hover with plain text  
**Impact:** Medium  

**What's Missing:**
- **Markdown/HTML rendering** - Rich documentation display
- **Code examples in docs** - Syntax highlighted examples
- **Link resolution** - Navigate to referenced types/methods
- **External documentation** - Link to official docs (JavaDoc, GroovyDoc)
- **Quick documentation popup** - Persistent doc window
- **Generate documentation stubs** - Create GroovyDoc templates

**Difficulty:** Low-Medium  
**Recommendation:** Implement Markdown rendering for GroovyDoc.

---

### 🟡 Workspace Enhancements

**Status:** Basic workspace support  
**Impact:** Medium  

**What's Missing:**
- **Multi-root workspace support** - Multiple project roots
- **Workspace-wide refactoring** - Rename across all projects
- **Workspace symbols with scope** - Filter by file, class, method
- **Project templates** - Generate new projects
- **Dependency management UI** - Add/remove dependencies

**Difficulty:** Medium  
**Recommendation:** Focus on multi-root support.

---

## Part 4: Missing Advanced Features (Low Priority)

### 🔵 Performance & Scalability

**Status:** Functional but can be improved  
**Impact:** High for large projects  

**What's Missing:**
- **Incremental compilation** - Only recompile changed files
- **Background indexing** - Non-blocking index updates
- **AST caching** - Cache parsed ASTs between sessions
- **Lazy loading** - Load symbols on demand
- **Parallel compilation** - Compile multiple files concurrently
- **Memory optimization** - Reduce memory footprint

**Difficulty:** High  
**Recommendation:** Profile first, then optimize hotspots.

---

### 🔵 Debugging Integration

**Status:** Basic Debug Adapter Protocol exists (in VS Code extension)  
**Impact:** Medium  

**What's Missing:**
- **Enhanced DAP support** - More robust debugging
- **Conditional breakpoints** - Break on condition
- **Logpoints** - Log without stopping
- **Data breakpoints** - Break on value change
- **Exception breakpoints** - Break on exception type
- **Step filtering** - Skip certain packages
- **Hot reload** - Update code during debugging

**Difficulty:** Medium-High  
**Recommendation:** Enhance existing DAP implementation.

---

### 🔵 Advanced Analysis

**Status:** Not implemented  
**Impact:** Low-Medium  

**What's Missing:**
- **Data flow analysis** - Trace variable values
- **Control flow analysis** - Unreachable code, missing returns
- **Taint analysis** - Security vulnerability detection
- **Null pointer analysis** - Advanced null safety
- **Immutability analysis** - Detect mutable state issues
- **Concurrency analysis** - Thread safety warnings

**Difficulty:** Very High  
**Recommendation:** Very low priority - complex to implement correctly.

---

### 🔵 Code Generation

**Status:** Not implemented  
**Impact:** Low-Medium  

**What's Missing:**
- Generate getters/setters
- Generate constructors
- Generate equals/hashCode/toString
- Implement interface methods
- Override superclass methods
- Create test stubs
- Create builders

**Difficulty:** Medium  
**Recommendation:** Use code actions to implement this.

---

### 🔵 Miscellaneous

**Status:** Various  
**Impact:** Low  

**What's Missing:**
- **Breadcrumbs** - Show current scope (package → class → method)
- **Moniker support** - Cross-project symbol linking
- **Inline values** - Show variable values during debug
- **Type hints on hover** - Show inferred types
- **Signature rename** - Rename parameters with method
- **Extract to file** - Move class to new file
- **Organize imports** - Sort and group imports
- **Optimize imports** - Remove unused, add missing

**Difficulty:** Low-Medium  
**Recommendation:** Implement as polish features.

---

## Part 5: Comparison with IntelliJ IDEA

The existing `FEATURES.md` already provides an excellent IntelliJ IDEA comparison. Here's a summary:

| Feature Category | IntelliJ IDEA | Groovy LS | Gap |
|-----------------|---------------|-----------|-----|
| **Navigation** | ✅✅✅ (Excellent) | ⚠️ (Good) | Small gap |
| **Code Completion** | ✅✅✅ (Excellent) | ⚠️ (Basic) | Large gap |
| **Refactoring** | ✅✅✅ (Full suite) | ⚠️ (Rename only) | Large gap |
| **Semantic Analysis** | ✅✅✅ (Advanced) | ⚠️ (Basic) | Large gap |
| **Build Tools** | ✅✅✅ (Full integration) | ⚠️ (Good) | Medium gap |
| **Formatting** | ✅✅ (Extensive) | ⚠️ (Basic) | Medium gap |
| **Testing** | ✅✅✅ (Full integration) | ❌ (None) | Large gap |
| **Debugging** | ✅✅✅ (Full DAP) | ⚠️ (Basic) | Large gap |
| **Dynamic Features** | ✅✅✅ (Excellent) | ⚠️ (Good) | Medium gap |
| **Performance** | ✅✅✅ (Optimized) | ⚠️ (Adequate) | Medium gap |

**Overall:** Groovy Language Server has ~40-50% feature parity with IntelliJ IDEA.

---

## Part 6: Recommendations & Prioritization

### 🔴 Critical (Implement First - 3-6 months)

1. **Code Actions** - Essential for productivity
   - Start with: Add missing import, remove unused import
   - Then: Extract method, extract variable
   - Finally: Generate code (getters/setters)

2. **Enhanced Diagnostics** - Catch more errors
   - Unused declarations (imports, variables, methods)
   - Type mismatches
   - Null safety warnings

3. **Code Lens** - Visual indicators
   - Reference counts
   - Implementation counts
   - Test runner buttons

### 🟡 High Priority (Next 6-12 months)

4. **Semantic Tokens** - Better syntax highlighting
5. **Folding Ranges** - Code folding support
6. **Inlay Hints** - Parameter names and types
7. **Smart Completion** - Type-aware filtering
8. **Testing Integration** - Spock/JUnit support
9. **Prepare Rename** - Validation before rename

### 🟢 Medium Priority (12-18 months)

10. **Advanced Refactoring** - Extract method, inline, move class
11. **Enhanced DSL Support** - Gradle/Jenkins completion
12. **Documentation Rendering** - Markdown/HTML docs
13. **Selection Range** - Smart selection expansion
14. **Document Links** - Clickable URLs/paths
15. **Performance Optimization** - Incremental compilation, caching

### 🔵 Low Priority (18+ months or polish phase)

16. **Pull Diagnostics** - Client-controlled diagnostics
17. **Advanced Analysis** - Data flow, control flow
18. **Linked Editing** - Simultaneous edits
19. **Document Color** - Color picker
20. **Debugging Enhancements** - Conditional breakpoints, hot reload
21. **Code Generation Templates** - Live templates
22. **Breadcrumbs** - Scope visualization

---

## Part 7: Implementation Strategy

### Phase 1: Foundation (Next 3 months)
- Implement Code Actions framework
- Add "Add missing import" and "Remove unused import" actions
- Implement Code Lens for reference counts
- Add basic semantic tokens support

### Phase 2: Enhanced Diagnostics (Months 3-6)
- Detect unused declarations
- Type mismatch warnings
- Improve error messages

### Phase 3: Editor Experience (Months 6-12)
- Implement folding ranges
- Add inlay hints
- Enhance completion with type-aware filtering
- Implement prepare rename

### Phase 4: Advanced Features (Months 12-18)
- Testing integration
- Advanced refactoring tools
- DSL support improvements
- Performance optimization

### Phase 5: Polish (Months 18-24+)
- Additional code actions
- Advanced analysis features
- UI enhancements
- Documentation improvements

---

## Part 8: Technical Considerations

### Architecture Recommendations

1. **Modular Design**
   - Separate concerns (parsing, analysis, code actions, etc.)
   - Use strategy pattern for extensibility
   - Plugin architecture for custom analyzers

2. **Performance**
   - Cache AST and compiled units
   - Use incremental compilation
   - Background processing with debouncing
   - Lazy loading of symbols

3. **Testing**
   - Comprehensive test suite for each feature
   - Integration tests for LSP protocol
   - Performance benchmarks

4. **Backward Compatibility**
   - Maintain compatibility with older Groovy versions
   - Graceful degradation for unsupported features

### Dependencies

Current key dependencies:
- `org.eclipse.lsp4j:0.24.0` - LSP implementation (latest)
- `org.apache.groovy:groovy:4.0.26` - Groovy compiler
- `org.gradle:gradle-tooling-api:9.3.0` - Gradle integration
- `io.github.classgraph:classgraph:4.8.184` - Classpath scanning

---

## Part 9: Community & Contribution

### How the Community Can Help

1. **Testing** - Report bugs and edge cases
2. **Feature Requests** - Prioritize based on real usage
3. **Contributions** - Implement features from this roadmap
4. **Documentation** - Improve setup guides and examples
5. **IDE Integration** - Test with different editors (VS Code, Sublime, Neovim, etc.)

### Contribution Priorities

For new contributors, good starting points:
1. **Easy:** Document links, color support, selection range
2. **Medium:** Folding ranges, code lens, prepare rename
3. **Hard:** Code actions, semantic tokens, advanced refactoring

---

## Conclusion

The Groovy Language Server is a **solid foundation** with good navigation and basic code intelligence. To reach feature parity with IntelliJ IDEA and provide a complete LSP implementation, the priorities are:

1. **Code Actions** - Enable quick fixes and refactoring
2. **Enhanced Diagnostics** - Catch more errors and warnings
3. **Code Lens** - Visual indicators for references/implementations
4. **Semantic Tokens** - Proper syntax highlighting
5. **Testing Integration** - Support for Spock/JUnit

Implementing these features over the next 12-18 months will significantly improve the developer experience and make the Groovy Language Server competitive with commercial IDEs.

---

**End of Analysis**

For questions or discussions, please refer to:
- GitHub Issues: https://github.com/billy-briggs-dev/groovy-language-server/issues
- Existing Roadmap: `FEATURES.md`
- LSP Specification: https://microsoft.github.io/language-server-protocol/
