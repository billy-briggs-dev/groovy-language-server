# LSP Feature Implementation Checklist

Quick reference for LSP feature completeness in Groovy Language Server.

## Legend
- ✅ Fully Implemented
- ⚠️ Partially Implemented
- ❌ Not Implemented
- 🔄 In Progress

---

## Text Document Synchronization
- ✅ `textDocument/didOpen`
- ✅ `textDocument/didChange` (FULL sync mode)
- ✅ `textDocument/didClose`
- ✅ `textDocument/didSave`
- ✅ `textDocument/willSave` (via configuration)

## Language Features

### Navigation & Symbols
- ✅ `textDocument/definition` - Go to definition
- ✅ `textDocument/typeDefinition` - Go to type definition
- ✅ `textDocument/implementation` - Find implementations
- ✅ `textDocument/references` - Find all references
- ✅ `textDocument/documentSymbol` - Document outline
- ✅ `workspace/symbol` - Workspace-wide search
- ❌ `textDocument/declaration` - Go to declaration
- ❌ `textDocument/prepareRename` - Validate rename
- ✅ `textDocument/rename` - Rename symbol

### Code Intelligence
- ✅ `textDocument/completion` - Code completion
  - ✅ Trigger characters: `.`, `(`, `,`
  - ⚠️ Missing: Smart filtering, post-fix completion
- ✅ `textDocument/hover` - Hover documentation
  - ⚠️ Missing: Rich HTML/Markdown rendering
- ✅ `textDocument/signatureHelp` - Function signatures
  - ✅ Trigger characters: `(`, `,`
- ❌ `textDocument/inlayHint` - Inline parameter/type hints
- ❌ `textDocument/selectionRange` - Smart selection

### Diagnostics
- ⚠️ `textDocument/publishDiagnostics` - Push model only
  - ✅ Syntax errors
  - ✅ Some semantic errors (undefined variables)
  - ❌ Type checking
  - ❌ Unused declarations
  - ❌ Code style violations
- ❌ `textDocument/diagnostic` - Pull diagnostics
- ❌ `workspace/diagnostic` - Workspace diagnostics

### Code Editing
- ✅ `textDocument/formatting` - Format document
- ✅ `textDocument/rangeFormatting` - Format selection
- ❌ `textDocument/onTypeFormatting` - Format on type
- ❌ `textDocument/codeAction` - Quick fixes & refactorings
- ❌ `codeAction/resolve` - Resolve code action
- ❌ `textDocument/linkedEditingRange` - Linked editing

### Visual Features
- ❌ `textDocument/codeLens` - Reference counts, actions
- ❌ `codeLens/resolve` - Resolve code lens
- ✅ `textDocument/foldingRange` - Code folding (NEW!)
- ❌ `textDocument/semanticTokens/full` - Syntax highlighting
- ❌ `textDocument/semanticTokens/full/delta` - Incremental highlighting
- ❌ `textDocument/semanticTokens/range` - Range highlighting
- ❌ `textDocument/documentColor` - Color picker
- ❌ `textDocument/colorPresentation` - Color format
- ❌ `textDocument/documentLink` - Clickable links
- ❌ `documentLink/resolve` - Resolve links

### Call & Type Hierarchy
- ✅ `callHierarchy/prepare` - Prepare call hierarchy
- ✅ `callHierarchy/incomingCalls` - Incoming calls
- ✅ `callHierarchy/outgoingCalls` - Outgoing calls
- ✅ `typeHierarchy/prepare` - Prepare type hierarchy
- ✅ `typeHierarchy/supertypes` - Navigate to supertypes
- ✅ `typeHierarchy/subtypes` - Navigate to subtypes

## Workspace Features
- ✅ `workspace/didChangeConfiguration` - Configuration changes
- ✅ `workspace/didChangeWatchedFiles` - File system events
- ❌ `workspace/workspaceFolders` - Multi-root workspaces
- ❌ `workspace/didCreateFiles` - File creation notification
- ❌ `workspace/didRenameFiles` - File rename notification
- ❌ `workspace/didDeleteFiles` - File deletion notification
- ❌ `workspace/willCreateFiles` - Pre-create hook
- ❌ `workspace/willRenameFiles` - Pre-rename hook
- ❌ `workspace/willDeleteFiles` - Pre-delete hook
- ❌ `workspace/applyEdit` - Apply workspace edits
- ✅ `workspace/executeCommand` - Custom commands
  - ✅ `groovy.findUsages`
  - ✅ `groovy.goToSuperMethod`

## Advanced Features
- ❌ `textDocument/moniker` - Cross-project symbols
- ❌ `textDocument/prepareTypeHierarchy` - Type hierarchy v2
- ❌ `textDocument/inlineValue` - Debug inline values
- ❌ `workspace/configuration` - Request configuration

## Server Capabilities

### Currently Enabled
```java
completionProvider: { triggerCharacters: [".", "(", ","] }
textDocumentSync: FULL
documentSymbolProvider: true
workspaceSymbolProvider: true
referencesProvider: true
definitionProvider: true
typeDefinitionProvider: true
implementationProvider: true
typeHierarchyProvider: true
callHierarchyProvider: true
hoverProvider: true
renameProvider: true
documentFormattingProvider: true
documentRangeFormattingProvider: true
signatureHelpProvider: { triggerCharacters: ["(", ","] }
executeCommandProvider: { commands: ["groovy.findUsages", "groovy.goToSuperMethod"] }
```

### Missing Capabilities
```java
codeActionProvider: false (MISSING)
codeLensProvider: false (MISSING)
foldingRangeProvider: false (MISSING)
semanticTokensProvider: false (MISSING)
inlayHintProvider: false (MISSING)
linkedEditingRangeProvider: false (MISSING)
selectionRangeProvider: false (MISSING)
documentLinkProvider: false (MISSING)
colorProvider: false (MISSING)
prepareRenameProvider: false (MISSING)
onTypeFormattingProvider: false (MISSING)
diagnosticProvider: false (MISSING - only push model)
```

---

## Priority Matrix

### 🔴 Critical (Blocks core workflows)
1. ❌ Code Actions - Quick fixes, refactorings
2. ❌ Enhanced Diagnostics - Type checking, unused declarations
3. ❌ Code Lens - Reference counts

### 🟡 High (Significantly improves UX)
4. ❌ Semantic Tokens - Proper syntax highlighting
5. ❌ Folding Ranges - Code folding
6. ❌ Inlay Hints - Parameter names
7. ❌ Prepare Rename - Validation

### 🟢 Medium (Nice to have)
8. ❌ Selection Range - Smart selection
9. ❌ Document Links - Clickable URLs
10. ❌ Pull Diagnostics - Client control

### 🔵 Low (Polish features)
11. ❌ Linked Editing - Simultaneous edits
12. ❌ Document Color - Color picker
13. ❌ On-Type Formatting - Format as you type

---

## Feature Coverage Summary

| Category | Implemented | Total | Coverage |
|----------|-------------|-------|----------|
| **Text Synchronization** | 5/5 | 5 | 100% |
| **Navigation** | 7/9 | 9 | 78% |
| **Code Intelligence** | 3/5 | 5 | 60% |
| **Diagnostics** | 1/3 | 3 | 33% |
| **Code Editing** | 2/6 | 6 | 33% |
| **Visual Features** | 1/10 | 10 | 10% |
| **Hierarchy** | 6/6 | 6 | 100% |
| **Workspace** | 3/13 | 13 | 23% |
| **Advanced** | 0/4 | 4 | 0% |
| **TOTAL** | 28/61 | 61 | 46% |

---

## Next Steps

See `MISSING_FEATURES_ANALYSIS.md` for detailed implementation recommendations.

**Quick wins for contributors:**
1. Implement `textDocument/codeLens` (reference counts)
2. Implement `textDocument/foldingRange` (code folding)
3. Implement `textDocument/prepareRename` (validation)
4. Implement `textDocument/selectionRange` (smart selection)
5. Add basic `textDocument/codeAction` (organize imports)

**End of Checklist**
