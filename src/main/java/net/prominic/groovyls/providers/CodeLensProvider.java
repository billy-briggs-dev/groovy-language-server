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
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class CodeLensProvider {
	private ASTNodeVisitor ast;

	public CodeLensProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<CodeLens>> provideCodeLenses(TextDocumentIdentifier textDocument) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		URI uri = URI.create(textDocument.getUri());
		List<ASTNode> nodes = ast.getNodes(uri);
		List<CodeLens> lenses = new ArrayList<>();

		for (ASTNode node : nodes) {
			if (node instanceof ClassNode) {
				ClassNode classNode = (ClassNode) node;
				// Add code lens for class
				CodeLens classLens = createCodeLensForNode(classNode, "class");
				if (classLens != null) {
					lenses.add(classLens);
				}

				// Add code lenses for methods
				for (MethodNode method : classNode.getMethods()) {
					// Skip synthetic methods
					if (method.isSynthetic() || method.getName().contains("$")) {
						continue;
					}
					CodeLens methodLens = createCodeLensForNode(method, "method");
					if (methodLens != null) {
						lenses.add(methodLens);
					}
				}

				// Add code lenses for properties
				for (PropertyNode property : classNode.getProperties()) {
					CodeLens propertyLens = createCodeLensForNode(property, "property");
					if (propertyLens != null) {
						lenses.add(propertyLens);
					}
				}

				// Add code lenses for fields
				for (FieldNode field : classNode.getFields()) {
					// Skip synthetic fields
					if (field.isSynthetic() || field.getName().contains("$")) {
						continue;
					}
					CodeLens fieldLens = createCodeLensForNode(field, "field");
					if (fieldLens != null) {
						lenses.add(fieldLens);
					}
				}
			} else if (node instanceof MethodNode) {
				// Top-level method (script method)
				MethodNode method = (MethodNode) node;
				if (!method.isSynthetic() && !method.getName().contains("$")) {
					CodeLens methodLens = createCodeLensForNode(method, "method");
					if (methodLens != null) {
						lenses.add(methodLens);
					}
				}
			}
		}

		return CompletableFuture.completedFuture(lenses);
	}

	private CodeLens createCodeLensForNode(ASTNode node, String nodeType) {
		Range range = GroovyLanguageServerUtils.astNodeToRange(node);
		if (range == null) {
			return null;
		}

		// Count references
		List<ASTNode> references = GroovyASTUtils.getReferences(node, ast);
		int referenceCount = references.size();

		// Create command text
		String commandTitle;
		if (referenceCount == 0) {
			commandTitle = "no references";
		} else if (referenceCount == 1) {
			commandTitle = "1 reference";
		} else {
			commandTitle = referenceCount + " references";
		}

		// Create command that will trigger "find references"
		Command command = new Command();
		command.setTitle(commandTitle);
		command.setCommand("editor.action.showReferences");
		
		// Create the code lens
		CodeLens lens = new CodeLens();
		lens.setRange(range);
		lens.setCommand(command);

		return lens;
	}
}
