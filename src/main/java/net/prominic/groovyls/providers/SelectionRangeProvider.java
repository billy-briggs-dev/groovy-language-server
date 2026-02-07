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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class SelectionRangeProvider {
	private ASTNodeVisitor ast;

	public SelectionRangeProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<SelectionRange>> provideSelectionRanges(TextDocumentIdentifier textDocument,
			List<Position> positions) {
		if (ast == null || positions == null || positions.isEmpty()) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		URI uri = URI.create(textDocument.getUri());
		List<ASTNode> nodes = ast.getNodes(uri);

		List<SelectionRange> results = new ArrayList<>();
		for (Position position : positions) {
			SelectionRange selectionRange = createSelectionRangeForPosition(nodes, position);
			if (selectionRange != null) {
				results.add(selectionRange);
			}
		}

		return CompletableFuture.completedFuture(results);
	}

	private SelectionRange createSelectionRangeForPosition(List<ASTNode> nodes, Position position) {
		// Find all nodes that contain the position, sorted from smallest to largest
		List<ASTNode> containingNodes = new ArrayList<>();
		for (ASTNode node : nodes) {
			if (nodeContainsPosition(node, position)) {
				containingNodes.add(node);
			}
		}

		if (containingNodes.isEmpty()) {
			return null;
		}

		// Sort by size (smallest first, so innermost node is first)
		containingNodes.sort((a, b) -> {
			int sizeA = (a.getLastLineNumber() - a.getLineNumber()) * 1000
					+ (a.getLastColumnNumber() - a.getColumnNumber());
			int sizeB = (b.getLastLineNumber() - b.getLineNumber()) * 1000
					+ (b.getLastColumnNumber() - b.getColumnNumber());
			return Integer.compare(sizeA, sizeB);
		});

		// Build nested selection ranges from innermost to outermost
		SelectionRange current = null;
		for (ASTNode node : containingNodes) {
			Range range = GroovyLanguageServerUtils.astNodeToRange(node);
			if (range != null) {
				SelectionRange selectionRange = new SelectionRange(range, current);
				current = selectionRange;
			}
		}

		return current;
	}

	private boolean nodeContainsPosition(ASTNode node, Position position) {
		if (node == null || node.getLineNumber() < 0 || node.getLastLineNumber() < 0) {
			return false;
		}

		int line = position.getLine() + 1; // LSP is 0-based, Groovy AST is 1-based
		int column = position.getCharacter() + 1;

		// Check if position is within node bounds
		if (line < node.getLineNumber() || line > node.getLastLineNumber()) {
			return false;
		}

		if (line == node.getLineNumber() && column < node.getColumnNumber()) {
			return false;
		}

		if (line == node.getLastLineNumber() && column > node.getLastColumnNumber()) {
			return false;
		}

		return true;
	}
}
