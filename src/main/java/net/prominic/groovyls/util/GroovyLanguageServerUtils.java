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
package net.prominic.groovyls.util;

import java.net.URI;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.syntax.SyntaxException;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class GroovyLanguageServerUtils {
	/**
	 * Converts a Groovy position to a LSP position.
	 * 
	 * May return null if the Groovy line is -1
	 */
	public static Position createGroovyPosition(int groovyLine, int groovyColumn) {
		if (groovyLine == -1) {
			return null;
		}
		if (groovyColumn == -1) {
			groovyColumn = 0;
		}
		int lspLine = groovyLine;
		if (lspLine > 0) {
			lspLine--;
		}
		int lspColumn = groovyColumn;
		if (lspColumn > 0) {
			lspColumn--;
		}
		return new Position(lspLine, lspColumn);
	}

	public static Range syntaxExceptionToRange(SyntaxException exception) {
		Position start = createGroovyPosition(exception.getStartLine(), exception.getStartColumn());
		if (start == null) {
			return null;
		}
		Position end = createGroovyPosition(exception.getEndLine(), exception.getEndColumn());
		if (end == null) {
			return null;
		}
		return new Range(start, end);
	}

	/**
	 * Converts a Groovy AST node to an LSP range.
	 * 
	 * May return null if the node's start line is -1
	 */
	public static Range astNodeToRange(ASTNode node) {
		Position start = createGroovyPosition(node.getLineNumber(), node.getColumnNumber());
		if (start == null) {
			return null;
		}
		Position end = createGroovyPosition(node.getLastLineNumber(), node.getLastColumnNumber());
		if (end == null) {
			end = start;
		}
		return new Range(start, end);
	}

	public static CompletionItemKind astNodeToCompletionItemKind(ASTNode node) {
		if (node instanceof ClassNode) {
			ClassNode classNode = (ClassNode) node;
			if (classNode.isInterface()) {
				return CompletionItemKind.Interface;
			} else if (classNode.isEnum()) {
				return CompletionItemKind.Enum;
			}
			return CompletionItemKind.Class;
		} else if (node instanceof MethodNode) {
			return CompletionItemKind.Method;
		} else if (node instanceof Variable) {
			if (node instanceof FieldNode || node instanceof PropertyNode) {
				return CompletionItemKind.Field;
			}
			return CompletionItemKind.Variable;
		}
		return CompletionItemKind.Property;
	}

	public static SymbolKind astNodeToSymbolKind(ASTNode node) {
		if (node instanceof ClassNode) {
			ClassNode classNode = (ClassNode) node;
			if (classNode.isInterface()) {
				return SymbolKind.Interface;
			} else if (classNode.isEnum()) {
				return SymbolKind.Enum;
			}
			return SymbolKind.Class;
		} else if (node instanceof MethodNode) {
			return SymbolKind.Method;
		} else if (node instanceof Variable) {
			if (node instanceof FieldNode || node instanceof PropertyNode) {
				return SymbolKind.Field;
			}
			return SymbolKind.Variable;
		}
		return SymbolKind.Property;
	}

	/**
	 * Converts a Groovy AST node to an LSP location.
	 * 
	 * May return null if the node's start line is -1
	 */
	public static Location astNodeToLocation(ASTNode node, URI uri) {
		Range range = astNodeToRange(node);
		if (range == null) {
			return null;
		}
		return new Location(uri.toString(), range);
	}

	public static DocumentSymbol astNodeToDocumentSymbol(ClassNode node) {
		return createDocumentSymbol(node, node.getName());
	}

	public static DocumentSymbol astNodeToDocumentSymbol(MethodNode node) {
		return createDocumentSymbol(node, node.getName());
	}

	public static DocumentSymbol astNodeToDocumentSymbol(Variable node) {
		if (!(node instanceof ASTNode)) {
			// DynamicVariable isn't an ASTNode
			return null;
		}
		return createDocumentSymbol((ASTNode) node, node.getName());
	}

	public static WorkspaceSymbol astNodeToWorkspaceSymbol(ClassNode node, URI uri, String parentName) {
		return createWorkspaceSymbol(node, node.getName(), uri, parentName);
	}

	public static WorkspaceSymbol astNodeToWorkspaceSymbol(MethodNode node, URI uri, String parentName) {
		return createWorkspaceSymbol(node, node.getName(), uri, parentName);
	}

	public static WorkspaceSymbol astNodeToWorkspaceSymbol(Variable node, URI uri, String parentName) {
		if (!(node instanceof ASTNode)) {
			// DynamicVariable isn't an ASTNode
			return null;
		}
		return createWorkspaceSymbol((ASTNode) node, node.getName(), uri, parentName);
	}

	private static DocumentSymbol createDocumentSymbol(ASTNode node, String name) {
		Range range = astNodeToRange(node);
		if (range == null) {
			return null;
		}
		SymbolKind symbolKind = astNodeToSymbolKind(node);
		return new DocumentSymbol(name, symbolKind, range, range);
	}

	private static WorkspaceSymbol createWorkspaceSymbol(ASTNode node, String name, URI uri, String parentName) {
		Location location = astNodeToLocation(node, uri);
		if (location == null) {
			return null;
		}
		SymbolKind symbolKind = astNodeToSymbolKind(node);
		WorkspaceSymbol info = new WorkspaceSymbol();
		info.setName(name);
		info.setKind(symbolKind);
		info.setLocation(Either.forLeft(location));
		info.setContainerName(parentName);
		return info;
	}
}