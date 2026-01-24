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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class SuperMethodProvider {
    private ASTNodeVisitor ast;

    public SuperMethodProvider(ASTNodeVisitor ast) {
        this.ast = ast;
    }

    public CompletableFuture<List<? extends Location>> provideSuperMethod(TextDocumentIdentifier textDocument,
            Position position) {
        if (ast == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        URI uri = URI.create(textDocument.getUri());
        ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
        MethodNode target = resolveMethodNode(offsetNode);
        if (target == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<MethodNode> superMethods = findSuperMethods(target);
        List<Location> locations = superMethods.stream().map(method -> {
            URI methodUri = ast.getURI(method);
            if (methodUri == null) {
                return null;
            }
            return GroovyLanguageServerUtils.astNodeToLocation(method, methodUri);
        }).filter(location -> location != null).collect(Collectors.toList());
        return CompletableFuture.completedFuture(locations);
    }

    private MethodNode resolveMethodNode(ASTNode node) {
        if (node == null) {
            return null;
        }
        ASTNode definition = GroovyASTUtils.getDefinition(node, true, ast);
        if (definition instanceof MethodNode) {
            return (MethodNode) definition;
        }
        if (node instanceof MethodNode) {
            return (MethodNode) node;
        }
        return (MethodNode) GroovyASTUtils.getEnclosingNodeOfType(node, MethodNode.class, ast);
    }

    private List<MethodNode> findSuperMethods(MethodNode target) {
        ClassNode declaringClass = target.getDeclaringClass();
        if (declaringClass == null) {
            return Collections.emptyList();
        }
        List<MethodNode> results = new ArrayList<>();
        ClassNode current = declaringClass.getSuperClass();
        while (current != null) {
            MethodNode match = findMatchingMethod(current, target);
            if (match != null) {
                results.add(match);
                break;
            }
            current = current.getSuperClass();
        }
        for (ClassNode iface : declaringClass.getInterfaces()) {
            MethodNode match = findMatchingMethod(iface, target);
            if (match != null) {
                results.add(match);
            }
        }
        return results;
    }

    private MethodNode findMatchingMethod(ClassNode classNode, MethodNode target) {
        if (classNode == null) {
            return null;
        }
        List<MethodNode> methods = classNode.getMethods(target.getName());
        for (MethodNode candidate : methods) {
            if (!isCompatibleOverride(candidate, target)) {
                continue;
            }
            return candidate;
        }
        return null;
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
