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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class TypeHierarchyProvider {
	private ASTNodeVisitor ast;

	public TypeHierarchyProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		URI uri = URI.create(params.getTextDocument().getUri());
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, params.getPosition().getLine(), 
				params.getPosition().getCharacter());
		
		if (offsetNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		// Get the definition - should be a ClassNode
		ASTNode definitionNode = GroovyASTUtils.getDefinition(offsetNode, true, ast);
		ClassNode classNode = null;
		
		if (definitionNode instanceof ClassNode) {
			classNode = (ClassNode) definitionNode;
		} else {
			// Try to get the type of the node (e.g., for variables)
			ClassNode typeNode = GroovyASTUtils.getTypeOfNode(offsetNode, ast);
			if (typeNode != null) {
				classNode = typeNode;
			}
		}
		
		if (classNode == null || classNode.getLineNumber() == -1) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		TypeHierarchyItem item = createTypeHierarchyItem(classNode);
		if (item == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		return CompletableFuture.completedFuture(Collections.singletonList(item));
	}

	public CompletableFuture<List<TypeHierarchyItem>> supertypes(TypeHierarchySupertypesParams params) {
		if (ast == null || params.getItem() == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		TypeHierarchyItem item = params.getItem();
		ClassNode classNode = findClassNodeByName(item.getName(), item.getUri());
		
		if (classNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		List<TypeHierarchyItem> supertypes = new ArrayList<>();
		
		// Add superclass
		ClassNode superClass = classNode.getSuperClass();
		if (superClass != null && !superClass.getName().equals("java.lang.Object") 
				&& !superClass.getName().equals("groovy.lang.GroovyObject")) {
			TypeHierarchyItem superItem = createTypeHierarchyItem(superClass);
			if (superItem != null) {
				supertypes.add(superItem);
			}
		}
		
		// Add interfaces
		for (ClassNode interfaceNode : classNode.getInterfaces()) {
			TypeHierarchyItem interfaceItem = createTypeHierarchyItem(interfaceNode);
			if (interfaceItem != null) {
				supertypes.add(interfaceItem);
			}
		}
		
		return CompletableFuture.completedFuture(supertypes);
	}

	public CompletableFuture<List<TypeHierarchyItem>> subtypes(TypeHierarchySubtypesParams params) {
		if (ast == null || params.getItem() == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		TypeHierarchyItem item = params.getItem();
		ClassNode classNode = findClassNodeByName(item.getName(), item.getUri());
		
		if (classNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		List<TypeHierarchyItem> subtypes = new ArrayList<>();
		
		// Search through all classes in the compilation units to find subtypes
		for (URI uri : ast.getURIs()) {
			for (ASTNode node : ast.getNodes(uri)) {
				if (node instanceof ClassNode) {
					ClassNode candidate = (ClassNode) node;
					if (isSubtype(candidate, classNode)) {
						TypeHierarchyItem subItem = createTypeHierarchyItem(candidate);
						if (subItem != null) {
							subtypes.add(subItem);
						}
					}
				}
			}
		}
		
		return CompletableFuture.completedFuture(subtypes);
	}

	private TypeHierarchyItem createTypeHierarchyItem(ClassNode classNode) {
		if (classNode == null || classNode.getLineNumber() == -1) {
			return null;
		}

		URI uri = ast.getURI(classNode);
		if (uri == null) {
			// For classes not in the workspace (e.g., from libraries), we can't provide a URI
			return null;
		}

		Range range = GroovyLanguageServerUtils.astNodeToRange(classNode);
		Range selectionRange = range; // Use the same range for selection

		SymbolKind kind;
		if (classNode.isInterface()) {
			kind = SymbolKind.Interface;
		} else if (classNode.isEnum()) {
			kind = SymbolKind.Enum;
		} else {
			kind = SymbolKind.Class;
		}

		TypeHierarchyItem item = new TypeHierarchyItem();
		item.setName(classNode.getName());
		item.setKind(kind);
		item.setUri(uri.toString());
		item.setRange(range);
		item.setSelectionRange(selectionRange);
		
		// Add deprecated tag if needed
		if (classNode.isDeprecated()) {
			item.setTags(Collections.singletonList(SymbolTag.Deprecated));
		}

		// Set detail (package name)
		String packageName = classNode.getPackageName();
		if (packageName != null && !packageName.isEmpty()) {
			item.setDetail(packageName);
		}

		return item;
	}

	private ClassNode findClassNodeByName(String className, String uriString) {
		if (className == null || uriString == null) {
			return null;
		}

		URI uri = URI.create(uriString);
		for (ASTNode node : ast.getNodes(uri)) {
			if (node instanceof ClassNode) {
				ClassNode classNode = (ClassNode) node;
				if (className.equals(classNode.getName())) {
					return classNode;
				}
			}
		}
		
		return null;
	}

	private boolean isSubtype(ClassNode candidate, ClassNode parent) {
		if (candidate == parent) {
			return false; // Not a subtype of itself
		}

		// Check if candidate extends parent
		ClassNode superClass = candidate.getSuperClass();
		while (superClass != null) {
			if (superClass.getName().equals(parent.getName())) {
				return true;
			}
			superClass = superClass.getSuperClass();
		}

		// Check if candidate implements parent (if parent is an interface)
		if (parent.isInterface()) {
			for (ClassNode interfaceNode : candidate.getAllInterfaces()) {
				if (interfaceNode.getName().equals(parent.getName())) {
					return true;
				}
			}
		}

		return false;
	}
}
