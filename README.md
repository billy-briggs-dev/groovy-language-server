# Groovy Language Server

A [language server](https://microsoft.github.io/language-server-protocol/) for [Groovy](http://groovy-lang.org/).

The following language server protocol requests are currently supported:

- completion
- definition
- documentSymbol
- formatting (document/range)
- hover
- references
- rename
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
