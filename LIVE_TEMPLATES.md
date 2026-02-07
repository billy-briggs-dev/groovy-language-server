# Live Templates Feature

The Groovy Language Server now supports live templates (code snippets) to accelerate coding.

## Built-in Templates

### Method Templates

#### `main` - Main Method
Expands to a static main method:
```groovy
static void main(String[] args) {
    // cursor here
}
```

#### `psvm` - Public Static Void Main
Expands to a public static void main method:
```groovy
public static void main(String[] args) {
    // cursor here
}
```

### Control Flow Templates

#### `for` - For Loop
Expands to a Groovy for-in loop with placeholders:
```groovy
for (item in collection) {
    // cursor here
}
```
- Tab stops at `item`, `collection`, and finally inside the loop body

### Surround-With Templates

#### `trycatch` - Try-Catch Block
Expands to a try-catch block:
```groovy
try {
    // code
} catch (Exception e) {
    // handle exception
}
```
- Tab stops at code section, exception type, exception variable, and handler

#### `ifelse` - If-Else Block
Expands to an if-else block:
```groovy
if (condition) {
    // true branch
} else {
    // false branch
}
```
- Tab stops at condition, true branch, and false branch

## How to Use

1. Start typing the template trigger (e.g., "ma" for "main")
2. The template will appear in the completion list with a "Snippet" icon
3. Select the template and press Enter
4. Use Tab to navigate between placeholders
5. Fill in the values as needed

## Custom Templates

Custom templates can be added programmatically via the `CompletionProvider.addCustomTemplate()` method:

```java
CompletionProvider provider = // get provider
provider.addCustomTemplate(
    "mytemplate",           // trigger
    "My Template",          // label
    "Custom template",      // description
    "def ${1:name} = ${0}"  // snippet with placeholders
);
```

### Snippet Syntax

Templates use LSP snippet syntax:
- `$0` - Final cursor position
- `${1}` - Tab stop 1 (numbered stops define tab order)
- `${1:defaultText}` - Tab stop with default/placeholder text
- `$1` - Reference to tab stop 1 (mirrors the value)

## Editor Support

The live templates feature is supported in any LSP-compatible editor:
- Visual Studio Code (with Groovy extension)
- Sublime Text (with LSP plugin)
- Moonshine IDE (native support)
- Any other editor with LSP support

## Future Enhancements

Potential future improvements:
- Configuration file support for custom templates
- Template categories/groups
- More built-in templates
- Template variables (e.g., `$CURRENT_DATE`, `$USER`)
- Wrap selection commands for surround-with templates
