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
- ✅ `textDocument/prepareRename` - Validate rename
- ✅ `textDocument/rename` - Rename symbol
- ✅ `textDocument/selectionRange` - Smart selection

### Code Intelligence
- ✅ `textDocument/completion` - Code completion
  - ✅ Trigger characters: `.`, `(`, `,`
  - ⚠️ Missing: Smart filtering, post-fix completion
- ✅ `textDocument/hover` - Hover documentation
  - ⚠️ Missing: Rich HTML/Markdown rendering
- ✅ `textDocument/signatureHelp` - Function signatures
  - ✅ Trigger characters: `(`, `,`
- ❌ `textDocument/inlayHint` - Inline parameter/type hints

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
- ✅ `textDocument/codeLens` - Reference counts, actions
- ❌ `codeLens/resolve` - Resolve code lens (not needed for basic functionality)
- ✅ `textDocument/foldingRange` - Code folding
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
renameProvider: { prepareProvider: true }
documentFormattingProvider: true
documentRangeFormattingProvider: true
foldingRangeProvider: true
selectionRangeProvider: true
codeLensProvider: { resolveProvider: false }
signatureHelpProvider: { triggerCharacters: ["(", ","] }
executeCommandProvider: { commands: ["groovy.findUsages", "groovy.goToSuperMethod"] }
```

### Missing Capabilities
```java
codeActionProvider: false (MISSING)
semanticTokensProvider: false (MISSING)
inlayHintProvider: false (MISSING)
linkedEditingRangeProvider: false (MISSING)
documentLinkProvider: false (MISSING)
colorProvider: false (MISSING)
onTypeFormattingProvider: false (MISSING)
diagnosticProvider: false (MISSING - only push model)
```

---

## Priority Matrix

### 🔴 Critical (Blocks core workflows)
1. ❌ Code Actions - Quick fixes, refactorings
2. ❌ Enhanced Diagnostics - Type checking, unused declarations

### 🟡 High (Significantly improves UX)
3. ❌ Semantic Tokens - Proper syntax highlighting
4. ❌ Inlay Hints - Parameter names

### 🟢 Medium (Nice to have)
5. ❌ Document Links - Clickable URLs
6. ❌ Pull Diagnostics - Client control

### 🔵 Low (Polish features)
11. ❌ Linked Editing - Simultaneous edits
12. ❌ Document Color - Color picker
13. ❌ On-Type Formatting - Format as you type

---

## Feature Coverage Summary

| Category | Implemented | Total | Coverage |
|----------|-------------|-------|----------|
| **Text Synchronization** | 5/5 | 5 | 100% |
| **Navigation** | 9/10 | 10 | 90% |
| **Code Intelligence** | 3/5 | 5 | 60% |
| **Diagnostics** | 1/3 | 3 | 33% |
| **Code Editing** | 2/6 | 6 | 33% |
| **Visual Features** | 3/11 | 11 | 27% |
| **Hierarchy** | 6/6 | 6 | 100% |
| **Workspace** | 3/13 | 13 | 23% |
| **Advanced** | 0/4 | 4 | 0% |
| **TOTAL** | 32/63 | 63 | 51% |

---

## Next Steps

See `MISSING_FEATURES_ANALYSIS.md` for detailed implementation recommendations.

**Quick wins for contributors:**
1. Add basic `textDocument/codeAction` (organize imports, quick fixes)
2. Implement `textDocument/inlayHint` (parameter names)
3. Implement `textDocument/documentLink` (clickable URLs)
4. Add enhanced diagnostics (unused declarations, type checking)
5. Implement `textDocument/semanticTokens` (syntax highlighting)

**End of Checklist**
