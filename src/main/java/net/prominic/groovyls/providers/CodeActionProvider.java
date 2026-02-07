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
package net.prominic.groovyls.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.FileContentsTracker;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;
import net.prominic.lsp.utils.Positions;
import net.prominic.lsp.utils.Ranges;

public class CodeActionProvider {
	private ASTNodeVisitor ast;
	private FileContentsTracker fileContentsTracker;

	public CodeActionProvider(ASTNodeVisitor ast, FileContentsTracker fileContentsTracker) {
		this.ast = ast;
		this.fileContentsTracker = fileContentsTracker;
	}

	public CompletableFuture<List<Either<Command, CodeAction>>> provideCodeActions(CodeActionParams params) {
		List<Either<Command, CodeAction>> codeActions = new ArrayList<>();

		if (ast == null) {
			return CompletableFuture.completedFuture(codeActions);
		}

		URI uri = URI.create(params.getTextDocument().getUri());
		Range range = params.getRange();

		// Get the node at the cursor position
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, range.getStart().getLine(),
				range.getStart().getCharacter());

		if (offsetNode != null) {
			// Extract variable - available when an expression is selected
			CodeAction extractVariable = createExtractVariableAction(uri, offsetNode, range);
			if (extractVariable != null) {
				codeActions.add(Either.forRight(extractVariable));
			}

			// Inline variable - available when a variable expression is selected
			CodeAction inlineVariable = createInlineVariableAction(uri, offsetNode);
			if (inlineVariable != null) {
				codeActions.add(Either.forRight(inlineVariable));
			}

			// Convert string - available when a string constant is selected
			CodeAction convertString = createConvertStringAction(uri, offsetNode);
			if (convertString != null) {
				codeActions.add(Either.forRight(convertString));
			}
		}

		return CompletableFuture.completedFuture(codeActions);
	}

	private CodeAction createExtractVariableAction(URI uri, ASTNode node, Range range) {
		// Only offer extract variable for expressions that aren't already variables
		if (node instanceof VariableExpression) {
			return null;
		}

		// Check if the node is an expression that can be extracted
		if (!(node instanceof org.codehaus.groovy.ast.expr.Expression)) {
			return null;
		}

		org.codehaus.groovy.ast.expr.Expression expr = (org.codehaus.groovy.ast.expr.Expression) node;

		// Don't extract simple literals (except strings which might be complex)
		if (expr instanceof ConstantExpression) {
			ConstantExpression constExpr = (ConstantExpression) expr;
			if (!(constExpr.getValue() instanceof String)) {
				return null;
			}
		}

		String contents = fileContentsTracker.getContents(uri);
		if (contents == null) {
			return null;
		}

		Range nodeRange = GroovyLanguageServerUtils.astNodeToRange(node);
		if (nodeRange == null) {
			return null;
		}

		String exprText = Ranges.getSubstring(contents, nodeRange);
		if (exprText == null || exprText.trim().isEmpty()) {
			return null;
		}

		CodeAction codeAction = new CodeAction("Extract to variable");
		codeAction.setKind(CodeActionKind.RefactorExtract);

		// Generate variable name based on expression
		String varName = generateVariableName(expr, exprText);

		// Create the edit to extract the variable
		// Insert the variable declaration on the line before
		Position insertPos = new Position(nodeRange.getStart().getLine(), 0);
		String indent = getIndentation(contents, nodeRange.getStart().getLine());
		String newText = indent + "def " + varName + " = " + exprText + "\n";

		// Replace the expression with the variable name
		TextEdit insertEdit = new TextEdit(new Range(insertPos, insertPos), newText);
		TextEdit replaceEdit = new TextEdit(nodeRange, varName);

		java.util.List<TextEdit> edits = new java.util.ArrayList<>();
		edits.add(insertEdit);
		edits.add(replaceEdit);

		WorkspaceEdit workspaceEdit = new WorkspaceEdit(
				Collections.singletonMap(uri.toString(), edits));
		codeAction.setEdit(workspaceEdit);

		return codeAction;
	}

	private String generateVariableName(org.codehaus.groovy.ast.expr.Expression expr, String exprText) {
		// Try to generate a meaningful variable name
		if (expr instanceof org.codehaus.groovy.ast.expr.MethodCallExpression) {
			org.codehaus.groovy.ast.expr.MethodCallExpression methodCall = (org.codehaus.groovy.ast.expr.MethodCallExpression) expr;
			String methodName = methodCall.getMethodAsString();
			if (methodName != null && !methodName.isEmpty()) {
				return methodName + "Result";
			}
		}

		if (expr instanceof ConstantExpression) {
			ConstantExpression constExpr = (ConstantExpression) expr;
			if (constExpr.getValue() instanceof String) {
				return "text";
			}
		}

		// Default name
		return "value";
	}

	private String getIndentation(String contents, int lineNumber) {
		String[] lines = contents.split("\n");
		if (lineNumber >= 0 && lineNumber < lines.length) {
			String line = lines[lineNumber];
			int i = 0;
			while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
				i++;
			}
			return line.substring(0, i);
		}
		return "";
	}

	private CodeAction createInlineVariableAction(URI uri, ASTNode node) {
		// Only offer inline for variable expressions
		if (!(node instanceof VariableExpression)) {
			return null;
		}

		VariableExpression varExpr = (VariableExpression) node;
		Variable accessedVar = varExpr.getAccessedVariable();
		
		// Can only inline local variables, not parameters or fields
		if (accessedVar == null || accessedVar instanceof org.codehaus.groovy.ast.Parameter) {
			return null;
		}

		// Find the definition of the variable
		ASTNode definition = null;
		if (ast != null) {
			definition = GroovyASTUtils.getDefinition(node, false, ast);
		}

		// For now, just create a disabled action as a placeholder
		// Full implementation would need to:
		// 1. Find the variable declaration and its initializer
		// 2. Find all references to the variable
		// 3. Replace each reference with the initializer expression
		// 4. Remove the variable declaration
		CodeAction codeAction = new CodeAction("Inline variable");
		codeAction.setKind(CodeActionKind.RefactorInline);

		codeAction.setDisabled(new org.eclipse.lsp4j.CodeActionDisabled(
				"Inline variable requires more complex implementation - use refactoring manually"));

		return codeAction;
	}

	private CodeAction createConvertStringAction(URI uri, ASTNode node) {
		if (!(node instanceof ConstantExpression)) {
			return null;
		}

		ConstantExpression constExpr = (ConstantExpression) node;
		if (!(constExpr.getValue() instanceof String)) {
			return null;
		}

		String contents = fileContentsTracker.getContents(uri);
		if (contents == null) {
			return null;
		}

		Range nodeRange = GroovyLanguageServerUtils.astNodeToRange(node);
		if (nodeRange == null) {
			return null;
		}

		String stringText = Ranges.getSubstring(contents, nodeRange);
		if (stringText == null || stringText.length() < 2) {
			return null;
		}

		// Determine if it's single-line or multi-line
		boolean isMultiLine = stringText.startsWith("\"\"\"") || stringText.startsWith("'''");
		boolean isSingleQuoted = stringText.startsWith("'");

		String actionTitle;
		String newText;

		if (isMultiLine) {
			// Convert from multi-line to single-line
			actionTitle = "Convert to single-line string";
			String stringValue = (String) constExpr.getValue();
			// Escape quotes and newlines
			String escaped = stringValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
					.replace("\r", "\\r").replace("\t", "\\t");
			newText = "\"" + escaped + "\"";
		} else {
			// Convert from single-line to multi-line
			actionTitle = "Convert to multi-line string";
			String stringValue = (String) constExpr.getValue();
			newText = "\"\"\"" + stringValue + "\"\"\"";
		}

		CodeAction codeAction = new CodeAction(actionTitle);
		codeAction.setKind(CodeActionKind.RefactorRewrite);

		TextEdit textEdit = new TextEdit(nodeRange, newText);
		WorkspaceEdit workspaceEdit = new WorkspaceEdit(
				Collections.singletonMap(uri.toString(), Collections.singletonList(textEdit)));
		codeAction.setEdit(workspaceEdit);

		return codeAction;
	}
}
