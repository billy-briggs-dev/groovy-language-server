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
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
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
            // this shouldn't happen, but let's avoid an exception if something
            // goes terribly wrong.
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }

        URI uri = URI.create(textDocument.getUri());
        ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
        ASTNode definitionNode = offsetNode != null ? GroovyASTUtils.getDefinition(offsetNode, true, ast) : null;

        if (definitionNode == null) {
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }

        List<ASTNode> implementations = findImplementations(definitionNode);
        List<Location> locations = implementations.stream().map(node -> {
            URI nodeUri = ast.getURI(node);
            if (nodeUri == null) {
                return null;
            }
            return GroovyLanguageServerUtils.astNodeToLocation(node, nodeUri);
        }).filter(location -> location != null).collect(Collectors.toList());

        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    private List<ASTNode> findImplementations(ASTNode definitionNode) {
        if (definitionNode instanceof MethodNode) {
            return findMethodImplementations((MethodNode) definitionNode);
        } else if (definitionNode instanceof ClassNode) {
            return findClassImplementations((ClassNode) definitionNode);
        }
        return Collections.emptyList();
    }

    private List<ASTNode> findClassImplementations(ClassNode target) {
        if (target == null) {
            return Collections.emptyList();
        }
        List<ASTNode> results = new ArrayList<>();
        for (ClassNode classNode : ast.getClassNodes()) {
            if (classNode == null || classNode.equals(target)) {
                continue;
            }
            if (isSubtypeOf(classNode, target)) {
                results.add(classNode);
            }
        }
        return results;
    }

    private List<ASTNode> findMethodImplementations(MethodNode targetMethod) {
        ClassNode targetClass = targetMethod.getDeclaringClass();
        if (targetClass == null) {
            return Collections.emptyList();
        }
        List<ASTNode> results = new ArrayList<>();
        for (ClassNode classNode : ast.getClassNodes()) {
            if (classNode == null || classNode.equals(targetClass)) {
                continue;
            }
            if (!isSubtypeOf(classNode, targetClass)) {
                continue;
            }
            List<MethodNode> methods = classNode.getMethods(targetMethod.getName());
            for (MethodNode method : methods) {
                if (!method.getDeclaringClass().equals(classNode)) {
                    continue;
                }
                if (isCompatibleOverride(method, targetMethod)) {
                    results.add(method);
                }
            }
        }
        return results;
    }

    private boolean isSubtypeOf(ClassNode candidate, ClassNode target) {
        if (candidate == null || target == null || candidate.equals(target)) {
            return false;
        }
        if (candidate.isDerivedFrom(target)) {
            return true;
        }
        return candidate.implementsInterface(target);
    }

    private boolean isCompatibleOverride(MethodNode candidate, MethodNode target) {
        if (candidate == null || target == null) {
            return false;
        }
        if (!candidate.getName().equals(target.getName())) {
            return false;
        }
        Parameter[] candidateParams = candidate.getParameters();
        Parameter[] targetParams = target.getParameters();
        if (candidateParams.length != targetParams.length) {
            return false;
        }
        for (int i = 0; i < candidateParams.length; i++) {
            ClassNode candidateType = candidateParams[i].getType();
            ClassNode targetType = targetParams[i].getType();
            if (candidateType == null || targetType == null) {
                continue;
            }
            if (ClassHelper.isDynamicTyped(candidateType) || ClassHelper.isDynamicTyped(targetType)) {
                continue;
            }
            if (!candidateType.equals(targetType)) {
                return false;
            }
        }
        return true;
    }
}
