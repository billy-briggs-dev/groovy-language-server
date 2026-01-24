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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class DocumentSymbolProvider {
	private ASTNodeVisitor ast;

	public DocumentSymbolProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> provideDocumentSymbols(
			TextDocumentIdentifier textDocument) {
		if (ast == null) {
			// this shouldn't happen, but let's avoid an exception if something
			// goes terribly wrong.
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		URI uri = URI.create(textDocument.getUri());
		List<ASTNode> nodes = ast.getNodes(uri);
		Map<ASTNode, DocumentSymbol> symbolByNode = new HashMap<>();
		List<ASTNode> symbolNodes = new ArrayList<>();
		for (ASTNode node : nodes) {
			DocumentSymbol symbol = null;
			if (node instanceof ClassNode) {
				symbol = GroovyLanguageServerUtils.astNodeToDocumentSymbol((ClassNode) node);
			} else if (node instanceof MethodNode) {
				symbol = GroovyLanguageServerUtils.astNodeToDocumentSymbol((MethodNode) node);
			} else if (node instanceof PropertyNode) {
				symbol = GroovyLanguageServerUtils.astNodeToDocumentSymbol((PropertyNode) node);
			} else if (node instanceof FieldNode) {
				symbol = GroovyLanguageServerUtils.astNodeToDocumentSymbol((FieldNode) node);
			}
			if (symbol != null) {
				symbolByNode.put(node, symbol);
				symbolNodes.add(node);
			}
		}

		List<DocumentSymbol> rootSymbols = new ArrayList<>();
		for (ASTNode node : symbolNodes) {
			DocumentSymbol symbol = symbolByNode.get(node);
			ASTNode parent = ast.getParent(node);
			DocumentSymbol parentSymbol = null;
			while (parent != null && parentSymbol == null) {
				parentSymbol = symbolByNode.get(parent);
				parent = ast.getParent(parent);
			}
			if (parentSymbol != null) {
				List<DocumentSymbol> children = parentSymbol.getChildren();
				if (children == null) {
					children = new ArrayList<>();
					parentSymbol.setChildren(children);
				}
				children.add(symbol);
			} else {
				rootSymbols.add(symbol);
			}
		}

		List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
		for (DocumentSymbol symbol : rootSymbols) {
			result.add(Either.forRight(symbol));
		}
		return CompletableFuture.completedFuture(result);
	}
}