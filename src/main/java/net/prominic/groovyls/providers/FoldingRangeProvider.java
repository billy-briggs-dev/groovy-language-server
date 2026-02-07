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
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;

public class FoldingRangeProvider {
	private ASTNodeVisitor ast;

	public FoldingRangeProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<FoldingRange>> provideFoldingRanges(TextDocumentIdentifier textDocument) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		URI uri = URI.create(textDocument.getUri());
		List<ASTNode> nodes = ast.getNodes(uri);
		List<FoldingRange> ranges = new ArrayList<>();

		// Collect imports for folding
		List<ImportNode> imports = new ArrayList<>();
		ModuleNode moduleNode = null;
		
		for (ASTNode node : nodes) {
			if (node instanceof ModuleNode) {
				moduleNode = (ModuleNode) node;
				break;
			}
		}
		
		// Fold imports section
		if (moduleNode != null && moduleNode.getImports() != null && !moduleNode.getImports().isEmpty()) {
			List<ImportNode> allImports = new ArrayList<>(moduleNode.getImports());
			if (moduleNode.getStarImports() != null) {
				allImports.addAll(moduleNode.getStarImports());
			}
			if (moduleNode.getStaticImports() != null) {
				allImports.addAll(moduleNode.getStaticImports().values());
			}
			if (moduleNode.getStaticStarImports() != null) {
				allImports.addAll(moduleNode.getStaticStarImports().values());
			}
			
			if (!allImports.isEmpty()) {
				int minLine = Integer.MAX_VALUE;
				int maxLine = Integer.MIN_VALUE;
				for (ImportNode importNode : allImports) {
					if (importNode.getLineNumber() > 0) {
						minLine = Math.min(minLine, importNode.getLineNumber());
						maxLine = Math.max(maxLine, importNode.getLastLineNumber());
					}
				}
				if (minLine != Integer.MAX_VALUE && maxLine != Integer.MIN_VALUE && maxLine > minLine) {
					FoldingRange range = new FoldingRange(minLine - 1, maxLine - 1);
					range.setKind(FoldingRangeKind.Imports);
					ranges.add(range);
				}
			}
		}

		// Fold classes, methods, and closures
		for (ASTNode node : nodes) {
			if (node instanceof ClassNode) {
				ranges.addAll(foldClass((ClassNode) node));
			} else if (node instanceof MethodNode) {
				FoldingRange range = createFoldingRange(node, FoldingRangeKind.Region);
				if (range != null) {
					ranges.add(range);
				}
			} else if (node instanceof ClosureExpression) {
				FoldingRange range = createFoldingRange(node, FoldingRangeKind.Region);
				if (range != null) {
					ranges.add(range);
				}
			}
		}

		return CompletableFuture.completedFuture(ranges);
	}

	private List<FoldingRange> foldClass(ClassNode classNode) {
		List<FoldingRange> ranges = new ArrayList<>();

		// Fold entire class
		FoldingRange classRange = createFoldingRange(classNode, FoldingRangeKind.Region);
		if (classRange != null) {
			ranges.add(classRange);
		}

		// Fold methods
		for (MethodNode method : classNode.getMethods()) {
			FoldingRange methodRange = createFoldingRange(method, FoldingRangeKind.Region);
			if (methodRange != null) {
				ranges.add(methodRange);
			}
		}

		return ranges;
	}

	private FoldingRange createFoldingRange(ASTNode node, String kind) {
		if (node == null || node.getLineNumber() < 0 || node.getLastLineNumber() < 0) {
			return null;
		}
		
		int startLine = node.getLineNumber() - 1; // LSP is 0-based
		int endLine = node.getLastLineNumber() - 1;
		
		// Only create folding range if it spans multiple lines
		if (endLine <= startLine) {
			return null;
		}
		
		FoldingRange range = new FoldingRange(startLine, endLine);
		range.setKind(kind);
		return range;
	}
}
