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
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class TypeHierarchyProvider {
    private ASTNodeVisitor ast;

    public TypeHierarchyProvider(ASTNodeVisitor ast) {
        this.ast = ast;
    }

    public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TextDocumentIdentifier textDocument,
            Position position) {
        if (ast == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        URI uri = URI.create(textDocument.getUri());
        ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
        ClassNode classNode = resolveClassNode(offsetNode);
        if (classNode == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        TypeHierarchyItem item = toTypeHierarchyItem(classNode);
        if (item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.completedFuture(Collections.singletonList(item));
    }

    public CompletableFuture<List<TypeHierarchyItem>> provideSupertypes(TypeHierarchyItem item) {
        if (ast == null || item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ClassNode classNode = resolveClassNode(item);
        if (classNode == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<TypeHierarchyItem> results = new ArrayList<>();
        ClassNode superClass = classNode.getSuperClass();
        if (superClass != null) {
            TypeHierarchyItem superItem = toTypeHierarchyItem(resolveClassNode(superClass));
            if (superItem != null) {
                results.add(superItem);
            }
        }
        for (ClassNode iface : classNode.getInterfaces()) {
            TypeHierarchyItem ifaceItem = toTypeHierarchyItem(resolveClassNode(iface));
            if (ifaceItem != null) {
                results.add(ifaceItem);
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    public CompletableFuture<List<TypeHierarchyItem>> provideSubtypes(TypeHierarchyItem item) {
        if (ast == null || item == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ClassNode target = resolveClassNode(item);
        if (target == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<TypeHierarchyItem> results = ast.getClassNodes().stream().filter(node -> {
            return node != null && !node.equals(target) && isSubtypeOf(node, target);
        }).map(this::toTypeHierarchyItem).filter(itemNode -> itemNode != null).collect(Collectors.toList());
        return CompletableFuture.completedFuture(results);
    }

    private ClassNode resolveClassNode(ASTNode node) {
        if (node == null) {
            return null;
        }
        ASTNode definition = GroovyASTUtils.getDefinition(node, true, ast);
        if (definition instanceof ClassNode) {
            return (ClassNode) definition;
        }
        if (definition instanceof MethodNode) {
            return ((MethodNode) definition).getDeclaringClass();
        }
        if (definition instanceof Variable) {
            ASTNode typeDef = GroovyASTUtils.getTypeDefinition(definition, ast);
            if (typeDef instanceof ClassNode) {
                return (ClassNode) typeDef;
            }
        }
        return null;
    }

    private ClassNode resolveClassNode(TypeHierarchyItem item) {
        if (item == null) {
            return null;
        }
        Object data = item.getData();
        String name = data instanceof String ? (String) data : item.getName();
        if (name == null) {
            return null;
        }
        for (ClassNode classNode : ast.getClassNodes()) {
            if (classNode == null) {
                continue;
            }
            if (name.equals(classNode.getName()) || name.equals(classNode.getNameWithoutPackage())) {
                return classNode;
            }
        }
        return null;
    }

    private ClassNode resolveClassNode(ClassNode node) {
        if (node == null) {
            return null;
        }
        for (ClassNode classNode : ast.getClassNodes()) {
            if (classNode != null && classNode.equals(node)) {
                return classNode;
            }
        }
        return null;
    }

    private TypeHierarchyItem toTypeHierarchyItem(ClassNode classNode) {
        if (classNode == null) {
            return null;
        }
        URI uri = ast.getURI(classNode);
        Range range = GroovyLanguageServerUtils.astNodeToRange(classNode);
        if (uri == null || range == null) {
            return null;
        }
        SymbolKind kind = GroovyLanguageServerUtils.astNodeToSymbolKind(classNode);
        String name = classNode.getNameWithoutPackage();
        String detail = classNode.getName();
        TypeHierarchyItem item = new TypeHierarchyItem(name, kind, uri.toString(), range, range, detail);
        item.setData(classNode.getName());
        return item;
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
}
