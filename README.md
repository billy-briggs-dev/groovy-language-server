# Groovy Language Server

A [language server](https://microsoft.github.io/language-server-protocol/) for [Groovy](http://groovy-lang.org/).

The following language server protocol requests are currently supported:

- completion
- definition
- documentSymbol
- foldingRange
- formatting (document/range)
- hover
- references
- rename
- selectionRange
- signatureHelp
- symbol
- typeDefinition

The sample VS Code extension also registers a minimal Debug Adapter Protocol implementation for Groovy with basic breakpoint, variable, and expression evaluation support.

The following configuration options are supported:

- groovy.java.home (`string` - sets a custom JDK path)
- groovy.classpath (`string[]` - sets a custom classpath to include _.jar_ files)
- groovy.formatting.formatOnSave (`boolean` - format on save)
- groovy.formatting.indentSize (`number` - indentation size)
- groovy.formatting.braceStyle (`sameLine` | `nextLine` - brace placement)
- groovy.formatting.spaceAroundOperators (`boolean` - spacing around operators)
- groovy.formatting.spaceAfterCommas (`boolean` - spacing after commas)
- groovy.formatting.spaceInsideBraces (`boolean` - spacing inside braces)
- groovy.classpathRecursive (`boolean` - when true, classpath folders are searched recursively for _.jar_ files)
- groovy.gradle.classpathScopes (`string[]` - Gradle dependency scopes to include: compile, runtime, test, provided)
- groovy.gradle.includeBuildscript (`boolean` - include buildSrc/buildscript outputs in the classpath)
- groovy.excludePatterns (`string[]` - glob patterns to exclude from project scanning)
- groovy.sourceRoots (`string[]` - explicit source roots; defaults to auto-detecting _src/main/groovy_ and _src/test/groovy_)
- groovy.maven.repositories (`string[]` - Maven repository URLs)
- groovy.maven.dependencies (`string[]` - Maven coordinates: `group:artifact:version[:classifier][@ext]`)

## Build

To build from the command line, run the following command:

```sh
./gradlew build
```

This will create _build/libs/groovy-language-server-all.jar_.

## Run

To run the language server, use the following command:

```sh
java -jar groovy-language-server-all.jar
```

Language server protocol messages are passed using standard I/O.

## Editors and IDEs

A sample language extension for Visual Studio Code is available in the _vscode-extension_ directory. There are no plans to release this extension to the VSCode Marketplace at this time.

Instructions for setting up the language server in Sublime Text is available in the _sublime-text_ directory.

Moonshine IDE natively provides a Grails project type that automatically configures the language server.

## Feature Documentation

For comprehensive documentation on features and roadmap:

- **[ANALYSIS_SUMMARY.md](ANALYSIS_SUMMARY.md)** - Executive summary of missing features analysis (start here!)
- **[FEATURES.md](FEATURES.md)** - Detailed feature parity roadmap with IntelliJ IDEA, implementation status, and planned features
- **[MISSING_FEATURES_ANALYSIS.md](MISSING_FEATURES_ANALYSIS.md)** - Complete analysis of missing LSP features with prioritization and recommendations
- **[LSP_FEATURE_CHECKLIST.md](LSP_FEATURE_CHECKLIST.md)** - Quick reference checklist for LSP feature implementation status
- **[CONTRIBUTING_FEATURES.md](CONTRIBUTING_FEATURES.md)** - Guide for implementing missing features (for contributors)
