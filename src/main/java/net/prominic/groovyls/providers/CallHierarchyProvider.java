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
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class CallHierarchyProvider {
    private ASTNodeVisitor ast;

    public CallHierarchyProvider(ASTNodeVisitor ast) {
        this.ast = ast;
    }

    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(TextDocumentIdentifier textDocument,
            Position position) {
        if (ast == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        URI uri = URI.create(textDocument.getUri());
        ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
        MethodNode methodNode = resolveMethodNode(offsetNode);
        if (methodNode == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        CallHierarchyItem item = toCallHierarchyItem(methodNode);
        if (item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.completedFuture(Collections.singletonList(item));
    }

    public CompletableFuture<List<CallHierarchyIncomingCall>> provideIncomingCalls(CallHierarchyItem item) {
        if (ast == null || item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        MethodNode target = resolveMethodNode(item);
        if (target == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Map<MethodNode, List<Range>> rangesByCaller = new HashMap<>();
        for (ASTNode node : ast.getNodes()) {
            if (!(node instanceof MethodCall)) {
                continue;
            }
            MethodNode resolved = GroovyASTUtils.getMethodFromCallExpression((MethodCall) node, ast);
            if (resolved == null || !matchesMethod(resolved, target)) {
                continue;
            }
            MethodNode caller = (MethodNode) GroovyASTUtils.getEnclosingNodeOfType(node, MethodNode.class, ast);
            if (caller == null) {
                continue;
            }
            Range range = getCallRange(node);
            if (range == null) {
                continue;
            }
            rangesByCaller.computeIfAbsent(caller, key -> new ArrayList<>()).add(range);
        }

        List<CallHierarchyIncomingCall> results = new ArrayList<>();
        for (Map.Entry<MethodNode, List<Range>> entry : rangesByCaller.entrySet()) {
            CallHierarchyItem fromItem = toCallHierarchyItem(entry.getKey());
            if (fromItem == null) {
                continue;
            }
            results.add(new CallHierarchyIncomingCall(fromItem, entry.getValue()));
        }
        return CompletableFuture.completedFuture(results);
    }

    public CompletableFuture<List<CallHierarchyOutgoingCall>> provideOutgoingCalls(CallHierarchyItem item) {
        if (ast == null || item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        MethodNode origin = resolveMethodNode(item);
        if (origin == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Map<MethodNode, List<Range>> rangesByCallee = new HashMap<>();
        for (ASTNode node : ast.getNodes()) {
            if (!(node instanceof MethodCall)) {
                continue;
            }
            MethodNode enclosingMethod = (MethodNode) GroovyASTUtils.getEnclosingNodeOfType(node, MethodNode.class,
                    ast);
            if (enclosingMethod == null || !matchesMethod(enclosingMethod, origin)) {
                continue;
            }
            MethodNode resolved = GroovyASTUtils.getMethodFromCallExpression((MethodCall) node, ast);
            if (resolved == null) {
                continue;
            }
            Range range = getCallRange(node);
            if (range == null) {
                continue;
            }
            rangesByCallee.computeIfAbsent(resolved, key -> new ArrayList<>()).add(range);
        }

        List<CallHierarchyOutgoingCall> results = rangesByCallee.entrySet().stream().map(entry -> {
            CallHierarchyItem toItem = toCallHierarchyItem(entry.getKey());
            if (toItem == null) {
                return null;
            }
            return new CallHierarchyOutgoingCall(toItem, entry.getValue());
        }).filter(call -> call != null).collect(Collectors.toList());

        return CompletableFuture.completedFuture(results);
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

    private MethodNode resolveMethodNode(CallHierarchyItem item) {
        if (item == null) {
            return null;
        }
        Object data = item.getData();
        String key = data instanceof String ? (String) data : null;
        if (key == null) {
            return null;
        }
        for (ASTNode node : ast.getNodes()) {
            if (!(node instanceof MethodNode)) {
                continue;
            }
            MethodNode method = (MethodNode) node;
            if (key.equals(createMethodKey(method))) {
                return method;
            }
        }
        return null;
    }

    private CallHierarchyItem toCallHierarchyItem(MethodNode method) {
        if (method == null) {
            return null;
        }
        URI uri = ast.getURI(method);
        Range range = GroovyLanguageServerUtils.astNodeToRange(method);
        if (uri == null || range == null) {
            return null;
        }
        CallHierarchyItem item = new CallHierarchyItem(method.getName(), SymbolKind.Method, uri.toString(), range,
                range);
        item.setDetail(method.getDeclaringClass() != null ? method.getDeclaringClass().getName() : null);
        item.setData(createMethodKey(method));
        return item;
    }

    private String createMethodKey(MethodNode method) {
        String className = method.getDeclaringClass() != null ? method.getDeclaringClass().getName() : "";
        int paramCount = method.getParameters() == null ? 0 : method.getParameters().length;
        return className + "#" + method.getName() + "/" + paramCount;
    }

    private boolean matchesMethod(MethodNode candidate, MethodNode target) {
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
        String candidateKey = createMethodKey(candidate);
        String targetKey = createMethodKey(target);
        return candidateKey.equals(targetKey) || candidate.equals(target);
    }

    private Range getCallRange(ASTNode node) {
        if (node instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) node;
            Range methodRange = GroovyLanguageServerUtils.astNodeToRange(call.getMethod());
            if (methodRange != null) {
                return methodRange;
            }
        }
        if (node instanceof StaticMethodCallExpression) {
            Range range = GroovyLanguageServerUtils.astNodeToRange(node);
            if (range != null) {
                return range;
            }
        }
        return GroovyLanguageServerUtils.astNodeToRange(node);
    }
}
