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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.ImportNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class UsageProvider {
    public static final String TYPE_METHOD_CALL = "methodCall";
    public static final String TYPE_CONSTRUCTOR_CALL = "constructorCall";
    public static final String TYPE_FIELD_ACCESS = "fieldAccess";
    public static final String TYPE_PROPERTY_ACCESS = "propertyAccess";
    public static final String TYPE_VARIABLE_REFERENCE = "variableReference";
    public static final String TYPE_TYPE_REFERENCE = "typeReference";
    public static final String TYPE_DECLARATION = "declaration";
    public static final String TYPE_REFERENCE = "reference";

    private ASTNodeVisitor ast;

    public UsageProvider(ASTNodeVisitor ast) {
        this.ast = ast;
    }

    public List<UsageItem> provideUsages(TextDocumentIdentifier textDocument, Position position,
            Set<String> typeFilter) {
        if (ast == null) {
            return Collections.emptyList();
        }
        URI uri = URI.create(textDocument.getUri());
        ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
        if (offsetNode == null) {
            return Collections.emptyList();
        }
        List<ASTNode> references = GroovyASTUtils.getReferences(offsetNode, ast);
        return references.stream().map(node -> toUsageItem(node)).filter(item -> item != null)
                .filter(item -> typeFilter == null || typeFilter.isEmpty() || typeFilter.contains(item.getType()))
                .collect(Collectors.toList());
    }

    private UsageItem toUsageItem(ASTNode node) {
        if (node == null) {
            return null;
        }
        URI uri = ast.getURI(node);
        if (uri == null) {
            return null;
        }
        Range range = resolveUsageRange(node);
        if (range == null) {
            return null;
        }
        Location location = new Location(uri.toString(), range);
        String type = resolveUsageType(node);
        String name = resolveSymbolName(node);
        return new UsageItem(type, location, name);
    }

    private Range resolveUsageRange(ASTNode node) {
        if (node instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) node;
            Range methodRange = GroovyLanguageServerUtils.astNodeToRange(call.getMethod());
            if (methodRange != null) {
                return methodRange;
            }
        }
        if (node instanceof PropertyExpression) {
            PropertyExpression prop = (PropertyExpression) node;
            Range propertyRange = GroovyLanguageServerUtils.astNodeToRange(prop.getProperty());
            if (propertyRange != null) {
                return propertyRange;
            }
        }
        if (node instanceof FieldExpression) {
            Range fieldRange = GroovyLanguageServerUtils.astNodeToRange(node);
            if (fieldRange != null) {
                return fieldRange;
            }
        }
        return GroovyLanguageServerUtils.astNodeToRange(node);
    }

    private String resolveUsageType(ASTNode node) {
        if (node instanceof MethodCallExpression) {
            return TYPE_METHOD_CALL;
        }
        if (node instanceof ConstructorCallExpression) {
            return TYPE_CONSTRUCTOR_CALL;
        }
        if (node instanceof PropertyExpression) {
            return TYPE_PROPERTY_ACCESS;
        }
        if (node instanceof FieldExpression) {
            return TYPE_FIELD_ACCESS;
        }
        if (node instanceof VariableExpression) {
            return TYPE_VARIABLE_REFERENCE;
        }
        if (node instanceof ClassExpression || node instanceof ImportNode) {
            return TYPE_TYPE_REFERENCE;
        }
        if (node instanceof MethodNode || node instanceof FieldNode || node instanceof PropertyNode
                || node instanceof ClassNode) {
            return TYPE_DECLARATION;
        }
        return TYPE_REFERENCE;
    }

    private String resolveSymbolName(ASTNode node) {
        if (node instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) node;
            return call.getMethodAsString();
        }
        if (node instanceof ConstructorCallExpression) {
            ConstructorCallExpression call = (ConstructorCallExpression) node;
            return call.getType() != null ? call.getType().getNameWithoutPackage() : null;
        }
        if (node instanceof PropertyExpression) {
            PropertyExpression prop = (PropertyExpression) node;
            return prop.getPropertyAsString();
        }
        if (node instanceof VariableExpression) {
            VariableExpression var = (VariableExpression) node;
            return var.getName();
        }
        if (node instanceof FieldExpression) {
            FieldExpression field = (FieldExpression) node;
            return field.getField() != null ? field.getField().getName() : null;
        }
        if (node instanceof ClassExpression) {
            ClassExpression cls = (ClassExpression) node;
            return cls.getType() != null ? cls.getType().getNameWithoutPackage() : null;
        }
        if (node instanceof ImportNode) {
            ImportNode imp = (ImportNode) node;
            return imp.getType() != null ? imp.getType().getNameWithoutPackage() : null;
        }
        if (node instanceof MethodNode) {
            return ((MethodNode) node).getName();
        }
        if (node instanceof FieldNode) {
            return ((FieldNode) node).getName();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getName();
        }
        if (node instanceof ClassNode) {
            return ((ClassNode) node).getNameWithoutPackage();
        }
        return null;
    }
}
