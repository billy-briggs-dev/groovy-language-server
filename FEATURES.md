Based on my research of IntelliJ IDEA's capabilities and the existing groovy-language-server issues, here's a comprehensive list to achieve **feature parity with IntelliJ IDEA**:

---

# Groovy Language Server: Feature Parity Roadmap with IntelliJ IDEA

## 🔴 Critical Features (Must Have)

### 1. **Dynamic Language Support**
- [x] **MetaClass method detection** (#103)
  - Track methods added via `SomeClass.metaClass.methodName = { }`
  - Support `methodMissing` and `propertyMissing`
  - Runtime method resolution
  
- [x] **AST Transformation support**
  - `@Delegate`
  - `@Mixin`
  - `@Category`
  - `@Immutable` (partial support exists #field validation)
  - `@Builder`
  - `@Canonical`
  - Custom AST transformations

- [x] **GString type inference**
  - Proper type resolution for interpolated strings
  - Template support

- [x] **Closure delegate resolution**
  - `@DelegatesTo` annotation support
  - DSL context inference (Gradle, Jenkins)

### 2. **Build System Integration**
- [ ] **Full Gradle support** (#26, #94, #106)
  - [x] Auto-detect Gradle projects
  - [x] Extract classpath from `build.gradle` / `build.gradle.kts`
  - [x] Multi-module project support
  - [ ] Transitive dependency resolution
  - [ ] Buildscript classpath support
  - [ ] Configuration-specific classpaths (compile, runtime, test)
  
- [ ] **Maven support**
  - Auto-detect Maven projects
  - Parse `pom.xml` for dependencies
  - Multi-module Maven projects
  
- [ ] **Grails support** (#41)
  - Grails project detection
  - Domain class intelligence
  - GORM support
  - GSP template support
  - Avoid duplicate class definitions from build output

### 3. **Semantic Analysis**
- [ ] **Undefined variable detection** (#95)
  - Flag undeclared variables
  - Distinguish between dynamic properties and errors
  
- [ ] **Type inference**
  - Local variable type inference
  - Method return type inference
  - Generic type parameter inference
  - Collection literal type inference
  
- [ ] **Null safety analysis**
  - `@NotNull` / `@Nullable` annotation support
  - Elvis operator awareness
  
- [ ] **Unreachable code detection**
  
- [ ] **Unused declaration detection**
  - Unused imports
  - Unused variables
  - Unused methods/fields

### 4. **Error Handling**
- [x] **Isolate syntax errors per file** (#54)
  - One file's errors shouldn't break LSP for entire workspace
  - Graceful degradation
  
- [ ] **Better error messages**
  - More context in diagnostics
  - Suggestions for common mistakes

## 🟡 High Priority Features

### 5. **Code Completion**
- [x] **Keyword completion** (#21)
  - `class`, `interface`, `enum`
  - `public`, `private`, `protected`
  - `static`, `final`, `abstract`
  - `if`, `else`, `for`, `while`, etc.
  
- [ ] **Smart type-aware completion**
  - Only show methods/fields matching expected type
  - Prioritize by usage frequency
  
- [ ] **Import completion**
  - Auto-add imports on completion
  - Organize imports
  
- [ ] **Parameter hints**
  - Show parameter names inline
  - Parameter info popup
  
- [ ] **Template/snippet completion**
  - Common patterns (getter/setter, etc.)

### 6. **Code Formatting** (#18)
- [ ] **Format document**
- [ ] **Format selection**
- [ ] **Format on save**
- [ ] **Configurable style**
  - Indent size
  - Brace style
  - Spacing rules

### 7. **Refactoring**
- [x] **Rename** (exists but may need improvement)
- [ ] **Extract method**
- [ ] **Extract variable**
- [ ] **Inline variable**
- [ ] **Change signature**
- [ ] **Move class**
- [ ] **Safe delete**
- [ ] **Convert between single/multi-line strings**

### 8. **Navigation**
- [x] **Go to definition** (exists)
- [x] **Find references** (exists but limited)
- [ ] **Find usages**
  - Show usages in tree view
  - Filter by type (method calls, field access, etc.)
  
- [ ] **Go to implementation**
- [ ] **Go to type definition**
- [ ] **Go to super method**
- [ ] **Find symbol** (workspace-wide)
- [ ] **File structure**
  - Outline view of current file
  
- [ ] **Type hierarchy**
- [ ] **Call hierarchy**

### 9. **Documentation**
- [x] **Hover for documentation** (exists)
- [ ] **Render GroovyDoc**
  - HTML rendering
  - Code examples
  - Link resolution
  
- [ ] **Quick documentation popup**
- [ ] **External documentation links**

### 10. **Performance** (#105)
- [x] **Debounce diagnostics** (#105)
  - Don't recompile on every keystroke
  - Batch changes
  
- [ ] **Incremental compilation**
  - Only recompile changed files
  
- [ ] **Background indexing**
  - Don't block UI during indexing
  
- [ ] **Caching**
  - Cache AST between sessions
  - Cache dependency resolution

## 🟢 Medium Priority Features

### 11. **Code Inspection & Linting**
- [ ] **Unused imports**
- [ ] **Redundant casts**
- [ ] **Unnecessary semicolons**
- [ ] **Empty blocks**
- [ ] **Duplicate code detection**
- [ ] **Code style violations**
- [ ] **Best practice suggestions**

### 12. **Testing Support**
- [ ] **Spock framework support**
  - Recognize Spock tests
  - Run individual tests
  - Test hierarchy
  
- [ ] **JUnit integration**
- [ ] **TestNG integration**
- [ ] **Test runner**
  - Run tests from editor
  - Show test results inline

### 13. **Debugging Integration**
- [ ] **Breakpoint support**
- [ ] **Variable inspection**
- [ ] **Expression evaluation**
- [ ] **Debug Adapter Protocol (DAP) implementation**

### 14. **Project Configuration**
- [ ] **Recursive classpath search** (#100, has PR #99)
- [ ] **Better classpath management**
  - GUI for adding JARs
  - Maven repository support
  
- [ ] **Source roots detection**
  - `src/main/groovy`
  - `src/test/groovy`
  
- [ ] **Exclude patterns** (#79)
  - Ignore `build/`, `target/`, etc.
  - Custom exclude patterns

### 15. **DSL Support**
- [ ] **Gradle DSL**
  - `dependencies { }` block completion
  - Plugin DSL support
  - Task configuration
  
- [ ] **Jenkins DSL**
  - Pipeline steps completion
  - `node`, `stage`, etc.
  
- [ ] **Custom DSL support**
  - `@DslMarker` annotation
  - Builder pattern recognition

### 16. **Code Generation**
- [ ] **Generate getters/setters**
- [ ] **Generate constructors**
- [ ] **Generate `toString()`**
- [ ] **Generate `equals()` / `hashCode()`**
- [ ] **Implement interface methods**
- [ ] **Override methods**

### 17. **Live Templates**
- [ ] **Built-in templates**
  - `main` → main method
  - `psvm` → public static void main
  - `for` → for loop
  
- [ ] **Custom templates**
- [ ] **Surround with templates**
  - try-catch
  - if-else

## 🔵 Nice to Have Features

### 18. **Advanced Analysis**
- [ ] **Data flow analysis**
  - Null pointer warnings
  - Resource leak detection
  
- [ ] **Control flow analysis**
  - Dead code detection
  - Missing return statements

### 19. **Code Folding**
- [ ] **Fold methods**
- [ ] **Fold imports**
- [ ] **Fold comments**
- [ ] **Fold blocks**
- [ ] **Custom fold regions**

### 20. **Breadcrumbs**
- [ ] **Show current scope**
  - Package → Class → Method

### 21. **Inlay Hints**
- [ ] **Parameter names**
- [ ] **Type hints**
- [ ] **Return type hints**

### 22. **Version Control Integration**
- [ ] **Git blame annotations**
- [ ] **Change markers in gutter**

### 23. **Code Lens**
- [ ] **Show references count**
- [ ] **Show implementations count**
- [ ] **Run test buttons**

### 24. **Semantic Highlighting**
- [ ] **Different colors for**
  - Local variables
  - Parameters
  - Fields
  - Static fields
  - Methods
  - Static methods

### 25. **Script Support**
- [ ] **Standalone script files** (#96)
  - Work outside Git repos
  - Script-specific features
  
- [ ] **Script bindings**
  - Recognize binding variables
  - Type inference from bindings

### 26. **GDK (Groovy Development Kit) Support**
- [ ] **GDK method completion**
  - Extension methods on Java types
  - Collection methods
  - IO methods
  
- [ ] **GDK documentation**

### 27. **Multi-language Support**
- [ ] **Java interop**
  - Seamless navigation Java ↔ Groovy
  - Mixed compilation
  
- [ ] **Kotlin DSL support**
  - For Gradle projects

### 28. **Configuration**
- [ ] **Language version selection**
  - Groovy 2.x vs 3.x vs 4.x
  
- [ ] **Compiler options**
  - Static compilation
  - Type checking level
  
- [ ] **Code style settings**
  - Per-project configuration

## 📊 Feature Parity Scorecard

| Category | IntelliJ IDEA | groovy-language-server | Gap |
|----------|---------------|------------------------|-----|
| **Basic Editing** | ✅ | ✅ | Small |
| **Code Completion** | ✅✅✅ | ⚠️ | Large |
| **Navigation** | ✅✅✅ | ⚠️ | Medium |
| **Refactoring** | ✅✅✅ | ⚠️ | Large |
| **Build Tools** | ✅✅✅ | ⚠️ | Medium |
| **Dynamic Features** | ✅✅✅ | ⚠️ | Large |
| **Semantic Analysis** | ✅✅✅ | ⚠️ | Large |
| **Formatting** | ✅✅ | ❌ | Medium |
| **Testing** | ✅✅✅ | ❌ | Large |
| **Debugging** | ✅✅✅ | ❌ | Large |
| **Performance** | ✅✅✅ | ⚠️ | Medium |

**Legend:** ✅ Full support | ⚠️ Partial support | ❌ No support

---

## 🎯 Recommended Implementation Order

### Phase 1: Foundation (3-6 months)
1. Fix critical bugs (#54, #105) — done
2. Full Gradle support (#26) — in progress
3. Keyword completion (#21) — done
4. Better error isolation — done

### Phase 2: Core Features (6-12 months)
5. MetaClass support (#103) — done
6. Code formatting (#18)
7. Semantic error detection (#95)
8. AST transformations
9. Type inference improvements

### Phase 3: Advanced Features (12-18 months)
10. Refactoring tools
11. DSL support (Gradle, Jenkins)
12. Testing integration
13. Code inspections
14. Advanced navigation

### Phase 4: Polish (18-24 months)
15. Debugging support
16. Performance optimization
17. Code generation
18. Live templates