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
import org.codehaus.groovy.ast.MethodNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class ImplementationProvider {
	private ASTNodeVisitor ast;

	public ImplementationProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> provideImplementation(
			TextDocumentIdentifier textDocument, Position position) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}
		
		URI uri = URI.create(textDocument.getUri());
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		
		if (offsetNode == null) {
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}

		// Get the definition of the node at the cursor
		ASTNode definitionNode = GroovyASTUtils.getDefinition(offsetNode, true, ast);
		if (definitionNode == null) {
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}

		List<Location> implementations = new ArrayList<>();

		// If the definition is a class (interface or abstract class), find implementations
		if (definitionNode instanceof ClassNode) {
			ClassNode classNode = (ClassNode) definitionNode;
			if (classNode.isInterface() || classNode.isAbstract()) {
				implementations.addAll(findClassImplementations(classNode));
			}
		}
		// If the definition is a method in an interface or abstract class, find overriding methods
		else if (definitionNode instanceof MethodNode) {
			MethodNode methodNode = (MethodNode) definitionNode;
			ClassNode declaringClass = methodNode.getDeclaringClass();
			if (declaringClass != null && (declaringClass.isInterface() || methodNode.isAbstract())) {
				implementations.addAll(findMethodImplementations(methodNode));
			}
		}

		return CompletableFuture.completedFuture(Either.forLeft(implementations));
	}

	private List<Location> findClassImplementations(ClassNode interfaceOrAbstractClass) {
		List<Location> implementations = new ArrayList<>();
		
		// Search through all classes in the compilation units
		for (URI uri : ast.getURIs()) {
			for (ASTNode node : ast.getNodes(uri)) {
				if (node instanceof ClassNode) {
					ClassNode classNode = (ClassNode) node;
					// Skip the class itself and skip interfaces/abstract classes
					if (classNode == interfaceOrAbstractClass || classNode.isInterface() 
							|| (classNode.isAbstract() && interfaceOrAbstractClass.isInterface())) {
						continue;
					}
					
					// Check if this class implements the interface or extends the abstract class
					if (isImplementation(classNode, interfaceOrAbstractClass)) {
						Location location = GroovyLanguageServerUtils.astNodeToLocation(classNode, uri);
						if (location != null) {
							implementations.add(location);
						}
					}
				}
			}
		}
		
		return implementations;
	}

	private boolean isImplementation(ClassNode classNode, ClassNode interfaceOrAbstractClass) {
		// Check if classNode implements the interface or extends the abstract class
		if (interfaceOrAbstractClass.isInterface()) {
			// Check all interfaces implemented by this class
			for (ClassNode interfaceNode : classNode.getAllInterfaces()) {
				if (interfaceNode.getName().equals(interfaceOrAbstractClass.getName())) {
					return true;
				}
			}
		} else {
			// Check superclass hierarchy for abstract class
			ClassNode superClass = classNode.getSuperClass();
			while (superClass != null) {
				if (superClass.getName().equals(interfaceOrAbstractClass.getName())) {
					return true;
				}
				superClass = superClass.getSuperClass();
			}
		}
		return false;
	}

	private List<Location> findMethodImplementations(MethodNode interfaceMethod) {
		List<Location> implementations = new ArrayList<>();
		ClassNode declaringClass = interfaceMethod.getDeclaringClass();
		
		if (declaringClass == null) {
			return implementations;
		}
		
		// Find all classes that implement the interface or extend the abstract class
		List<ClassNode> implementingClasses = new ArrayList<>();
		for (URI uri : ast.getURIs()) {
			for (ASTNode node : ast.getNodes(uri)) {
				if (node instanceof ClassNode) {
					ClassNode classNode = (ClassNode) node;
					if (isImplementation(classNode, declaringClass)) {
						implementingClasses.add(classNode);
					}
				}
			}
		}
		
		// Find the method in each implementing class
		for (ClassNode implementingClass : implementingClasses) {
			MethodNode implementingMethod = findMatchingMethod(implementingClass, interfaceMethod);
			if (implementingMethod != null) {
				URI uri = ast.getURI(implementingMethod);
				if (uri != null) {
					Location location = GroovyLanguageServerUtils.astNodeToLocation(implementingMethod, uri);
					if (location != null) {
						implementations.add(location);
					}
				}
			}
		}
		
		return implementations;
	}

	private MethodNode findMatchingMethod(ClassNode classNode, MethodNode methodToMatch) {
		String methodName = methodToMatch.getName();
		int paramCount = methodToMatch.getParameters() != null ? methodToMatch.getParameters().length : 0;
		
		for (MethodNode method : classNode.getMethods()) {
			if (method.getName().equals(methodName)) {
				int methodParamCount = method.getParameters() != null ? method.getParameters().length : 0;
				if (methodParamCount == paramCount) {
					// Simple match by name and parameter count
					// A more sophisticated check would compare parameter types
					return method;
				}
			}
		}
		
		return null;
	}
}
