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
package net.prominic.groovyls.compiler.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class GroovyASTUtils {
    private static final List<String> DELEGATES_TO_ANNOTATIONS = List.of("DelegatesTo", "groovy.lang.DelegatesTo");

    public static ASTNode getEnclosingNodeOfType(ASTNode offsetNode, Class<? extends ASTNode> nodeType,
            ASTNodeVisitor astVisitor) {
        ASTNode current = offsetNode;
        while (current != null) {
            if (nodeType.isInstance(current)) {
                return current;
            }
            current = astVisitor.getParent(current);
        }
        return null;
    }

    public static ASTNode getDefinition(ASTNode node, boolean strict, ASTNodeVisitor astVisitor) {
        if (node == null) {
            return null;
        }
        ASTNode parentNode = astVisitor.getParent(node);
        if (node instanceof ExpressionStatement) {
            ExpressionStatement statement = (ExpressionStatement) node;
            node = statement.getExpression();
        }
        if (node instanceof ClassNode) {
            return tryToResolveOriginalClassNode((ClassNode) node, strict, astVisitor);
        } else if (node instanceof ConstructorCallExpression) {
            ConstructorCallExpression callExpression = (ConstructorCallExpression) node;
            return GroovyASTUtils.getMethodFromCallExpression(callExpression, astVisitor);
        } else if (node instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression) node;
            MethodNode methodNode = GroovyASTUtils.getMethodFromCallExpression(methodCallExpression, astVisitor);
            if (methodNode != null) {
                return methodNode;
            }
        } else if (node instanceof PropertyExpression) {
            PropertyExpression propertyExpression = (PropertyExpression) node;
            PropertyNode propNode = GroovyASTUtils.getPropertyFromExpression(propertyExpression, astVisitor);
            if (propNode != null) {
                return propNode;
            }
            FieldNode fieldNode = GroovyASTUtils.getFieldFromExpression(propertyExpression, astVisitor);
            if (fieldNode != null) {
                return fieldNode;
            }
        } else if (node instanceof DeclarationExpression) {
            DeclarationExpression declExpression = (DeclarationExpression) node;
            if (!declExpression.isMultipleAssignmentDeclaration()) {
                ClassNode originType = declExpression.getVariableExpression().getOriginType();
                return tryToResolveOriginalClassNode(originType, strict, astVisitor);
            }
        } else if (node instanceof ConstantExpression) {
            String text = ((ConstantExpression) node).getText();
            if ("it".equals(text) || "delegate".equals(text)) {
                ClassNode delegatesTo = resolveDelegatesToType(node, astVisitor);
                if (delegatesTo != null) {
                    return delegatesTo;
                }
            }
        } else if (node instanceof ClassExpression) {
            ClassExpression classExpression = (ClassExpression) node;
            return tryToResolveOriginalClassNode(classExpression.getType(), strict, astVisitor);
        } else if (node instanceof ImportNode) {
            ImportNode importNode = (ImportNode) node;
            return tryToResolveOriginalClassNode(importNode.getType(), strict, astVisitor);
        } else if (node instanceof MethodNode) {
            return node;
        } else if (node instanceof ConstantExpression && parentNode != null) {
            if (parentNode instanceof MethodCallExpression) {
                MethodCallExpression methodCallExpression = (MethodCallExpression) parentNode;
                return GroovyASTUtils.getMethodFromCallExpression(methodCallExpression, astVisitor);
            } else if (parentNode instanceof PropertyExpression) {
                PropertyExpression propertyExpression = (PropertyExpression) parentNode;
                PropertyNode propNode = GroovyASTUtils.getPropertyFromExpression(propertyExpression, astVisitor);
                if (propNode != null) {
                    return propNode;
                }
                FieldNode fieldNode = GroovyASTUtils.getFieldFromExpression(propertyExpression, astVisitor);
                if (fieldNode != null) {
                    return fieldNode;
                }
                ASTNode fallback = findPropertyOrFieldByName(propertyExpression.getProperty().getText(), astVisitor);
                if (fallback != null) {
                    return fallback;
                }
            }
        } else if (node instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) node;
            Variable accessedVariable = variableExpression.getAccessedVariable();
            if (accessedVariable instanceof ASTNode) {
                return (ASTNode) accessedVariable;
            }
            // DynamicVariable is not an ASTNode, so skip it
            return null;
        } else if (node instanceof Variable) {
            return node;
        }
        return null;
    }

    private static ASTNode findPropertyOrFieldByName(String name, ASTNodeVisitor astVisitor) {
        if (name == null || astVisitor == null) {
            return null;
        }
        ASTNode match = null;
        for (ClassNode classNode : astVisitor.getClassNodes()) {
            PropertyNode prop = classNode.getProperty(name);
            if (prop != null) {
                if (match != null && match != prop) {
                    return null;
                }
                match = prop;
            }
            FieldNode field = classNode.getField(name);
            if (field != null) {
                if (match != null && match != field) {
                    return null;
                }
                match = field;
            }
        }
        return match;
    }

    public static ASTNode getTypeDefinition(ASTNode node, ASTNodeVisitor astVisitor) {
        ASTNode definitionNode = getDefinition(node, false, astVisitor);
        if (definitionNode == null) {
            return null;
        }
        if (definitionNode instanceof MethodNode) {
            MethodNode method = (MethodNode) definitionNode;
            ClassNode returnType = inferReturnType(method, astVisitor);
            return tryToResolveOriginalClassNode(returnType, true, astVisitor);
        } else if (definitionNode instanceof Variable) {
            Variable variable = (Variable) definitionNode;
            ClassNode variableType = variable.getOriginType();
            if (variableType == null || variableType == ClassHelper.DYNAMIC_TYPE) {
                variableType = getTypeOfNode(definitionNode, astVisitor);
            }
            return tryToResolveOriginalClassNode(variableType, true, astVisitor);
        }
        return null;
    }

    public static List<ASTNode> getReferences(ASTNode node, ASTNodeVisitor ast) {
        ASTNode definitionNode = getDefinition(node, true, ast);
        if (definitionNode == null) {
            return Collections.emptyList();
        }
        return ast.getNodes().stream().filter(otherNode -> {
            ASTNode otherDefinition = getDefinition(otherNode, false, ast);
            return definitionNode.equals(otherDefinition) && node.getLineNumber() != -1 && node.getColumnNumber() != -1;
        }).collect(Collectors.toList());
    }

    private static ClassNode tryToResolveOriginalClassNode(ClassNode node, boolean strict, ASTNodeVisitor ast) {
        for (ClassNode originalNode : ast.getClassNodes()) {
            if (originalNode.equals(node)) {
                return originalNode;
            }
        }
        if (strict) {
            return null;
        }
        return node;
    }

    public static PropertyNode getPropertyFromExpression(PropertyExpression node, ASTNodeVisitor astVisitor) {
        ClassNode classNode = getTypeOfNode(node.getObjectExpression(), astVisitor);
        if (classNode != null) {
            return classNode.getProperty(node.getProperty().getText());
        }
        return null;
    }

    public static FieldNode getFieldFromExpression(PropertyExpression node, ASTNodeVisitor astVisitor) {
        ClassNode classNode = getTypeOfNode(node.getObjectExpression(), astVisitor);
        if (classNode != null) {
            return classNode.getField(node.getProperty().getText());
        }
        return null;
    }

    public static List<FieldNode> getFieldsForLeftSideOfPropertyExpression(Expression node, ASTNodeVisitor astVisitor) {
        ClassNode classNode = getTypeOfNode(node, astVisitor);
        if (classNode != null) {
            List<ClassNode> classNodes = new ArrayList<>();
            classNodes.add(classNode);

            boolean statics = node instanceof ClassExpression;

            List<FieldNode> result = new ArrayList<>();
            int i = 0;
            while (i < classNodes.size()) {
                ClassNode current = classNodes.get(i);

                result.addAll(current.getFields().stream().filter(fieldNode -> {
                    return statics ? fieldNode.isStatic() : !fieldNode.isStatic();
                }).collect(Collectors.toList()));

                if (current.isInterface()) {
                    for (ClassNode interfaceNode : current.getInterfaces()) {
                        classNodes.add(interfaceNode);
                    }
                } else {
                    ClassNode superClassNode = null;
                    try {
                        superClassNode = current.getSuperClass();
                    } catch (NoClassDefFoundError e) {
                        // this is fine, we'll just treat it as null
                    }
                    if (superClassNode != null) {
                        classNodes.add(superClassNode);
                    }
                }
                i++;
            }
            return result;
        }
        return Collections.emptyList();
    }

    public static List<PropertyNode> getPropertiesForLeftSideOfPropertyExpression(Expression node,
            ASTNodeVisitor astVisitor) {
        ClassNode classNode = getTypeOfNode(node, astVisitor);
        if (classNode != null) {
            List<ClassNode> classNodes = new ArrayList<>();
            classNodes.add(classNode);

            boolean statics = node instanceof ClassExpression;

            List<PropertyNode> result = new ArrayList<>();
            int i = 0;
            while (i < classNodes.size()) {
                ClassNode current = classNodes.get(i);

                result.addAll(current.getProperties().stream().filter(propNode -> {
                    return statics ? propNode.isStatic() : !propNode.isStatic();
                }).collect(Collectors.toList()));

                if (current.isInterface()) {
                    for (ClassNode interfaceNode : current.getInterfaces()) {
                        classNodes.add(interfaceNode);
                    }
                } else {
                    ClassNode superClassNode = null;
                    try {
                        superClassNode = current.getSuperClass();
                    } catch (NoClassDefFoundError e) {
                        // this is fine, we'll just treat it as null
                    }
                    if (superClassNode != null) {
                        classNodes.add(superClassNode);
                    }
                }
                i++;
            }
        }
        return Collections.emptyList();
    }

    public static List<MethodNode> getMethodsForLeftSideOfPropertyExpression(Expression node,
            ASTNodeVisitor astVisitor) {
        ClassNode classNode = getTypeOfNode(node, astVisitor);
        if (classNode != null) {
            List<ClassNode> classNodes = new ArrayList<>();
            classNodes.add(classNode);

            boolean statics = node instanceof ClassExpression;

            List<MethodNode> result = new ArrayList<>();
            int i = 0;
            while (i < classNodes.size()) {
                ClassNode current = classNodes.get(i);

                result.addAll(current.getMethods().stream().filter(methodNode -> {
                    return statics ? methodNode.isStatic() : !methodNode.isStatic();
                }).collect(Collectors.toList()));

                result.addAll(astVisitor.getMetaClassMethods(current));

                if (current.isInterface()) {
                    for (ClassNode interfaceNode : current.getInterfaces()) {
                        classNodes.add(interfaceNode);
                    }
                } else {
                    ClassNode superClassNode = null;
                    try {
                        superClassNode = current.getSuperClass();
                    } catch (NoClassDefFoundError e) {
                        // this is fine, we'll just treat it as null
                    }
                    if (superClassNode != null) {
                        classNodes.add(superClassNode);
                    }
                }
                i++;
            }
            return result;
        }
        return Collections.emptyList();
    }

    public static ClassNode getTypeOfNode(ASTNode node, ASTNodeVisitor astVisitor) {
        if (node instanceof BinaryExpression) {
            BinaryExpression binaryExpr = (BinaryExpression) node;
            Expression leftExpr = binaryExpr.getLeftExpression();
            String opText = binaryExpr.getOperation().getText();
            if (opText != null && opText.contains("[")) {
                ClassNode leftType = leftExpr.getType();
                boolean needsGenericResolution = leftType == null || leftType == ClassHelper.DYNAMIC_TYPE
                        || leftType.getGenericsTypes() == null || leftType.getGenericsTypes().length == 0;
                if (needsGenericResolution) {
                    ASTNode defNode = GroovyASTUtils.getDefinition(leftExpr, false, astVisitor);
                    if (defNode != null) {
                        ClassNode defType = getTypeOfNode(defNode, astVisitor);
                        if (defType != null && (leftType == null || leftType == ClassHelper.DYNAMIC_TYPE
                                || defType.getGenericsTypes() != null && defType.getGenericsTypes().length > 0)) {
                            leftType = defType;
                        }
                    }
                }
                if ((leftType == null || leftType == ClassHelper.DYNAMIC_TYPE)
                        && leftExpr instanceof VariableExpression) {
                    String varName = ((VariableExpression) leftExpr).getName();
                    for (ASTNode candidate : astVisitor.getNodes()) {
                        if (candidate instanceof DeclarationExpression) {
                            DeclarationExpression decl = (DeclarationExpression) candidate;
                            if (decl.getVariableExpression() != null
                                    && varName.equals(decl.getVariableExpression().getName())) {
                                ClassNode origin = decl.getVariableExpression().getOriginType();
                                if (origin != null && origin != ClassHelper.DYNAMIC_TYPE) {
                                    leftType = origin;
                                    break;
                                }
                                ClassNode varType = decl.getVariableExpression().getType();
                                if (varType != null && varType != ClassHelper.DYNAMIC_TYPE) {
                                    leftType = varType;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (leftType != null && leftType.isArray()) {
                    return leftType.getComponentType();
                }
                if (leftType != null && leftType.getGenericsTypes() != null
                        && leftType.getGenericsTypes().length > 0) {
                    GenericsType[] generics = leftType.getGenericsTypes();
                    if ((ClassHelper.MAP_TYPE.equals(leftType) || leftType.implementsInterface(ClassHelper.MAP_TYPE))
                            && generics.length > 1) {
                        return generics[1].getType();
                    }
                    return generics[0].getType();
                }
            }
        } else if (node instanceof GStringExpression) {
            return ClassHelper.STRING_TYPE;
        } else if (node instanceof ConstantExpression) {
            ConstantExpression expression = (ConstantExpression) node;
            if (expression.getValue() instanceof String) {
                return ClassHelper.STRING_TYPE;
            }
        } else if (node instanceof ListExpression) {
            ListExpression expression = (ListExpression) node;
            ClassNode elementType = inferCommonType(expression.getExpressions(), astVisitor);
            ClassNode listType = ClassHelper.LIST_TYPE.getPlainNodeReference();
            if (elementType != null) {
                listType.setGenericsTypes(new GenericsType[] { new GenericsType(elementType) });
            }
            return listType;
        } else if (node instanceof MapExpression) {
            MapExpression expression = (MapExpression) node;
            List<MapEntryExpression> entries = expression.getMapEntryExpressions();
            List<Expression> keyExpressions = new ArrayList<>();
            List<Expression> valueExpressions = new ArrayList<>();
            for (MapEntryExpression entry : entries) {
                if (entry.getKeyExpression() != null) {
                    keyExpressions.add(entry.getKeyExpression());
                }
                if (entry.getValueExpression() != null) {
                    valueExpressions.add(entry.getValueExpression());
                }
            }
            ClassNode keyType = inferCommonType(keyExpressions, astVisitor);
            ClassNode valueType = inferCommonType(valueExpressions, astVisitor);
            ClassNode mapType = ClassHelper.MAP_TYPE.getPlainNodeReference();
            if (keyType != null && valueType != null) {
                mapType.setGenericsTypes(new GenericsType[] { new GenericsType(keyType), new GenericsType(valueType) });
            }
            return mapType;
        } else if (node instanceof ClassExpression) {
            ClassExpression expression = (ClassExpression) node;
            // This means it's an expression like this: SomeClass.someProp
            return expression.getType();
        } else if (node instanceof ConstructorCallExpression) {
            ConstructorCallExpression expression = (ConstructorCallExpression) node;
            return expression.getType();
        } else if (node instanceof MethodCallExpression) {
            MethodCallExpression expression = (MethodCallExpression) node;
            MethodNode methodNode = GroovyASTUtils.getMethodFromCallExpression(expression, astVisitor);
            if (methodNode == null) {
                methodNode = resolveMethodByName(expression, astVisitor);
            }
            if (methodNode != null) {
                return inferReturnType(methodNode, astVisitor);
            }
            return expression.getType();
        } else if (node instanceof PropertyExpression) {
            PropertyExpression expression = (PropertyExpression) node;
            PropertyNode propNode = GroovyASTUtils.getPropertyFromExpression(expression, astVisitor);
            if (propNode != null) {
                return getTypeOfNode(propNode, astVisitor);
            }
            FieldNode fieldNode = GroovyASTUtils.getFieldFromExpression(expression, astVisitor);
            if (fieldNode != null) {
                return getTypeOfNode(fieldNode, astVisitor);
            }
            return expression.getType();
        } else if (node instanceof Variable) {
            Variable var = (Variable) node;
            if (var.getName().equals("this")) {
                ClassNode enclosingClass = (ClassNode) getEnclosingNodeOfType(node, ClassNode.class, astVisitor);
                if (enclosingClass != null) {
                    return enclosingClass;
                }
            } else if ("it".equals(var.getName()) || "delegate".equals(var.getName())) {
                ClassNode delegatesTo = resolveDelegatesToType(node, astVisitor);
                if (delegatesTo != null) {
                    return delegatesTo;
                }
            } else if (var.isDynamicTyped() || var.getType() == ClassHelper.OBJECT_TYPE || var.getType() == null
                    || var.getOriginType() == ClassHelper.OBJECT_TYPE) {
                ASTNode defNode = GroovyASTUtils.getDefinition(node, false, astVisitor);
                if (defNode instanceof Variable) {
                    Variable defVar = (Variable) defNode;
                    if (defVar.hasInitialExpression()) {
                        return getTypeOfNode(defVar.getInitialExpression(), astVisitor);
                    } else {
                        ASTNode declNode = astVisitor.getParent(defNode);
                        if (declNode instanceof DeclarationExpression) {
                            DeclarationExpression decl = (DeclarationExpression) declNode;
                            return getTypeOfNode(decl.getRightExpression(), astVisitor);
                        }
                    }
                } else if (defNode == null && var.getName() != null) {
                    String varName = var.getName();
                    for (ASTNode candidate : astVisitor.getNodes()) {
                        if (candidate instanceof DeclarationExpression) {
                            DeclarationExpression decl = (DeclarationExpression) candidate;
                            if (decl.getVariableExpression() != null
                                    && varName.equals(decl.getVariableExpression().getName())) {
                                return getTypeOfNode(decl.getRightExpression(), astVisitor);
                            }
                        }
                    }
                }
                if (var.getName() != null) {
                    ClassNode assignedType = findAssignedTypeForVariable(var.getName(), node, astVisitor);
                    if (assignedType != null) {
                        return assignedType;
                    }
                }
            }
            if (var.getOriginType() != null) {
                return var.getOriginType();
            }
            if (var.getType() != null && var.getType() != ClassHelper.DYNAMIC_TYPE) {
                return var.getType();
            }
            if (var.getName() != null) {
                ClassNode assignedType = findAssignedTypeForVariable(var.getName(), node, astVisitor);
                if (assignedType != null) {
                    return assignedType;
                }
            }
        }
        if (node instanceof Expression) {
            Expression expression = (Expression) node;
            return expression.getType();
        }
        return null;
    }

    private static ClassNode inferReturnType(MethodNode method, ASTNodeVisitor astVisitor) {
        if (method == null) {
            return null;
        }
        ClassNode declaredType = method.getReturnType();
        boolean dynamicReturnType = method.isDynamicReturnType();
        if (!dynamicReturnType && declaredType != null
                && declaredType != ClassHelper.DYNAMIC_TYPE
                && declaredType != ClassHelper.OBJECT_TYPE) {
            return declaredType;
        }
        Statement code = method.getCode();
        if (code == null) {
            return declaredType != null ? declaredType : ClassHelper.OBJECT_TYPE;
        }
        List<ClassNode> returnTypes = new ArrayList<>();
        collectReturnTypes(code, returnTypes, astVisitor);
        ClassNode commonType = mergeReturnTypes(returnTypes);
        if (commonType != null) {
            return commonType;
        }
        return declaredType != null ? declaredType : ClassHelper.OBJECT_TYPE;
    }

    private static boolean collectReturnTypes(Statement statement, List<ClassNode> returnTypes,
            ASTNodeVisitor astVisitor) {
        if (statement == null) {
            return false;
        }
        if (statement instanceof ReturnStatement) {
            ReturnStatement returnStatement = (ReturnStatement) statement;
            Expression expression = returnStatement.getExpression();
            if (expression != null) {
                ClassNode returnType = getTypeOfNode(expression, astVisitor);
                if (returnType != null) {
                    returnTypes.add(returnType);
                }
            }
            return true;
        }
        if (statement instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) statement;
            List<Statement> statements = block.getStatements();
            boolean hasExplicitReturn = false;
            for (Statement child : statements) {
                if (collectReturnTypes(child, returnTypes, astVisitor)) {
                    hasExplicitReturn = true;
                }
            }
            if (!hasExplicitReturn && !statements.isEmpty()) {
                Statement last = statements.get(statements.size() - 1);
                if (last instanceof ExpressionStatement) {
                    Expression expression = ((ExpressionStatement) last).getExpression();
                    ClassNode returnType = getTypeOfNode(expression, astVisitor);
                    if (returnType != null) {
                        returnTypes.add(returnType);
                    }
                }
            }
            return hasExplicitReturn;
        }
        if (statement instanceof ExpressionStatement) {
            Expression expression = ((ExpressionStatement) statement).getExpression();
            ClassNode returnType = getTypeOfNode(expression, astVisitor);
            if (returnType != null) {
                returnTypes.add(returnType);
            }
            return false;
        }
        return false;
    }

    private static ClassNode mergeReturnTypes(List<ClassNode> returnTypes) {
        if (returnTypes == null || returnTypes.isEmpty()) {
            return null;
        }
        ClassNode commonType = null;
        for (ClassNode returnType : returnTypes) {
            if (returnType == null) {
                continue;
            }
            if (commonType == null) {
                commonType = returnType;
                continue;
            }
            if (!commonType.equals(returnType)) {
                return ClassHelper.OBJECT_TYPE;
            }
        }
        return commonType != null ? commonType : ClassHelper.OBJECT_TYPE;
    }

    private static ClassNode findAssignedTypeForVariable(String varName, ASTNode usageNode,
            ASTNodeVisitor astVisitor) {
        if (varName == null || astVisitor == null) {
            return null;
        }
        int usageLine = usageNode != null ? usageNode.getLineNumber() : -1;
        int usageColumn = usageNode != null ? usageNode.getColumnNumber() : -1;
        ClassNode bestType = null;
        int bestLine = -1;
        int bestColumn = -1;
        for (ASTNode candidate : astVisitor.getNodes()) {
            if (!(candidate instanceof BinaryExpression)) {
                if (candidate instanceof DeclarationExpression) {
                    DeclarationExpression decl = (DeclarationExpression) candidate;
                    if (decl.getVariableExpression() != null
                            && varName.equals(decl.getVariableExpression().getName())
                            && decl.getRightExpression() != null) {
                        ClassNode type = getTypeOfNode(decl.getRightExpression(), astVisitor);
                        if (type != null) {
                            if (bestType == null) {
                                bestType = type;
                                bestLine = candidate.getLineNumber();
                                bestColumn = candidate.getColumnNumber();
                            }
                        }
                    }
                }
                continue;
            }
            BinaryExpression binary = (BinaryExpression) candidate;
            if (!isAssignmentOperator(binary.getOperation() != null ? binary.getOperation().getText() : null)) {
                continue;
            }
            Expression left = binary.getLeftExpression();
            if (!(left instanceof VariableExpression)) {
                continue;
            }
            String name = ((VariableExpression) left).getName();
            if (!varName.equals(name)) {
                continue;
            }
            int line = candidate.getLineNumber();
            int column = candidate.getColumnNumber();
            if (usageLine != -1 && line != -1) {
                if (line > usageLine || (line == usageLine && column > usageColumn)) {
                    continue;
                }
            }
            ClassNode type = getTypeOfNode(binary.getRightExpression(), astVisitor);
            if (type == null) {
                continue;
            }
            if (bestType == null || line > bestLine || (line == bestLine && column > bestColumn)) {
                bestType = type;
                bestLine = line;
                bestColumn = column;
            }
        }
        return bestType;
    }

    private static boolean isAssignmentOperator(String opText) {
        if (opText == null) {
            return false;
        }
        String op = opText.trim();
        if (op.equals("==") || op.equals("!=")) {
            return false;
        }
        return op.equals("=") || op.endsWith("=");
    }

    private static ClassNode inferCommonType(List<Expression> expressions, ASTNodeVisitor astVisitor) {
        if (expressions == null || expressions.isEmpty()) {
            return ClassHelper.OBJECT_TYPE;
        }
        ClassNode commonType = null;
        for (Expression expression : expressions) {
            ClassNode expressionType = getTypeOfNode(expression, astVisitor);
            if (expressionType == null) {
                continue;
            }
            if (commonType == null) {
                commonType = expressionType;
                continue;
            }
            if (!commonType.equals(expressionType)) {
                return ClassHelper.OBJECT_TYPE;
            }
        }
        return commonType != null ? commonType : ClassHelper.OBJECT_TYPE;
    }

    private static ClassNode resolveDelegatesToType(ASTNode node, ASTNodeVisitor astVisitor) {
        ASTNode closureNode = getEnclosingNodeOfType(node, ClosureExpression.class, astVisitor);
        if (!(closureNode instanceof ClosureExpression)) {
            return null;
        }
        ClosureExpression closure = (ClosureExpression) closureNode;
        ASTNode enclosingCall = getEnclosingNodeOfType(closure, MethodCallExpression.class, astVisitor);
        if (!(enclosingCall instanceof MethodCallExpression)) {
            return null;
        }
        MethodCallExpression call = (MethodCallExpression) enclosingCall;
        MethodNode method = getMethodFromCallExpression(call, astVisitor);
        if (method == null) {
            method = resolveMethodByName(call, astVisitor);
        }
        if (method == null) {
            return null;
        }
        int index = findClosureArgumentIndex(call, closure);
        if (index < 0) {
            index = findClosureParameterIndex(method);
        }
        if (index < 0 || index >= method.getParameters().length) {
            return null;
        }
        Parameter parameter = method.getParameters()[index];
        AnnotationNode delegatesTo = findAnnotation(parameter, DELEGATES_TO_ANNOTATIONS);
        if (delegatesTo == null) {
            return null;
        }
        Expression value = delegatesTo.getMember("value");
        ClassNode resolved = resolveClassNodeFromExpression(value);
        if (resolved != null) {
            return resolved;
        }
        return parameter.getType();
    }

    private static int findClosureArgumentIndex(MethodCallExpression call, ClosureExpression closure) {
        if (call.getArguments() instanceof ArgumentListExpression) {
            ArgumentListExpression args = (ArgumentListExpression) call.getArguments();
            List<Expression> expressions = args.getExpressions();
            for (int i = 0; i < expressions.size(); i++) {
                if (expressions.get(i) == closure) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findClosureParameterIndex(MethodNode method) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            ClassNode type = params[i].getType();
            if (type != null && type.getName().equals("groovy.lang.Closure")) {
                return i;
            }
        }
        return -1;
    }

    private static MethodNode resolveMethodByName(MethodCallExpression call, ASTNodeVisitor astVisitor) {
        if (call == null || astVisitor == null) {
            return null;
        }
        String methodName = call.getMethodAsString();
        if (methodName == null) {
            return null;
        }
        ClassNode owner = getTypeOfNode(call.getObjectExpression(), astVisitor);
        if (owner == null && call.getObjectExpression() instanceof ClassExpression) {
            owner = ((ClassExpression) call.getObjectExpression()).getType();
        }
        if (owner == null && call.getObjectExpression() instanceof VariableExpression) {
            String name = ((VariableExpression) call.getObjectExpression()).getName();
            owner = findClassNodeByName(name, astVisitor);
        }
        if (owner == null && call.isImplicitThis()) {
            ASTNode enclosingClass = getEnclosingNodeOfType(call, ClassNode.class, astVisitor);
            if (enclosingClass instanceof ClassNode) {
                owner = (ClassNode) enclosingClass;
            }
        }
        if (owner == null) {
            return null;
        }
        boolean statics = call.getObjectExpression() instanceof ClassExpression;
        for (MethodNode method : owner.getMethods(methodName)) {
            if (statics == method.isStatic()) {
                return method;
            }
        }
        return owner.getMethods(methodName).stream().findFirst().orElse(null);
    }

    private static ClassNode findClassNodeByName(String name, ASTNodeVisitor astVisitor) {
        if (name == null || astVisitor == null) {
            return null;
        }
        for (ClassNode classNode : astVisitor.getClassNodes()) {
            if (name.equals(classNode.getName()) || name.equals(classNode.getNameWithoutPackage())) {
                return classNode;
            }
        }
        return null;
    }

    private static AnnotationNode findAnnotation(AnnotatedNode node, List<String> names) {
        if (node == null) {
            return null;
        }
        for (AnnotationNode annotation : node.getAnnotations()) {
            String full = annotation.getClassNode().getName();
            String simple = annotation.getClassNode().getNameWithoutPackage();
            if (names.contains(full) || (simple != null && names.contains(simple))) {
                return annotation;
            }
        }
        return null;
    }

    private static ClassNode resolveClassNodeFromExpression(Expression expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof ClassExpression) {
            ClassNode exprType = ((ClassExpression) expr).getType();
            if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type
                    && exprType.getGenericsTypes() != null && exprType.getGenericsTypes().length > 0) {
                return exprType.getGenericsTypes()[0].getType();
            }
            if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type) {
                return new ClassNode(expr.getText(), 0, ClassHelper.OBJECT_TYPE);
            }
            return exprType;
        }
        if (expr instanceof ConstantExpression) {
            String name = ((ConstantExpression) expr).getText();
            if (name != null && !name.isBlank()) {
                return new ClassNode(name, 0, ClassHelper.OBJECT_TYPE);
            }
        }
        if (expr instanceof VariableExpression) {
            String name = ((VariableExpression) expr).getName();
            if (name != null && !name.isBlank()) {
                return new ClassNode(name, 0, ClassHelper.OBJECT_TYPE);
            }
        }
        return null;
    }

    public static List<MethodNode> getMethodOverloadsFromCallExpression(MethodCall node, ASTNodeVisitor astVisitor) {
        if (node instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpr = (MethodCallExpression) node;
            ClassNode leftType = getTypeOfNode(methodCallExpr.getObjectExpression(), astVisitor);
            if (leftType == null && methodCallExpr.getObjectExpression() instanceof VariableExpression) {
                VariableExpression varExpr = (VariableExpression) methodCallExpr.getObjectExpression();
                if ("this".equals(varExpr.getName())) {
                    ASTNode enclosingClass = getEnclosingNodeOfType(methodCallExpr, ClassNode.class, astVisitor);
                    if (enclosingClass instanceof ClassNode) {
                        leftType = (ClassNode) enclosingClass;
                    }
                }
            }
            if (leftType == null && methodCallExpr.isImplicitThis()) {
                ASTNode enclosingClass = getEnclosingNodeOfType(methodCallExpr, ClassNode.class, astVisitor);
                if (enclosingClass instanceof ClassNode) {
                    leftType = (ClassNode) enclosingClass;
                }
            }
            if (leftType == null) {
                ASTNode enclosingClass = getEnclosingNodeOfType(methodCallExpr, ClassNode.class, astVisitor);
                if (enclosingClass instanceof ClassNode) {
                    leftType = (ClassNode) enclosingClass;
                }
            }
            if (leftType != null) {
                List<MethodNode> methods = new ArrayList<>(leftType.getMethods(methodCallExpr.getMethod().getText()));
                methods.addAll(astVisitor.getMetaClassMethods(leftType).stream()
                        .filter(method -> method.getName().equals(methodCallExpr.getMethod().getText()))
                        .collect(Collectors.toList()));
                return methods;
            }
        } else if (node instanceof ConstructorCallExpression) {
            ConstructorCallExpression constructorCallExpr = (ConstructorCallExpression) node;
            ClassNode constructorType = constructorCallExpr.getType();
            if (constructorType != null) {
                return constructorType.getDeclaredConstructors().stream().map(constructor -> (MethodNode) constructor)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    public static MethodNode getMethodFromCallExpression(MethodCall node, ASTNodeVisitor astVisitor) {
        return getMethodFromCallExpression(node, astVisitor, -1);
    }

    public static MethodNode getMethodFromCallExpression(MethodCall node, ASTNodeVisitor astVisitor, int argIndex) {
        List<MethodNode> possibleMethods = getMethodOverloadsFromCallExpression(node, astVisitor);
        if (!possibleMethods.isEmpty() && node.getArguments() instanceof ArgumentListExpression) {
            ArgumentListExpression actualArguments = (ArgumentListExpression) node.getArguments();
            MethodNode foundMethod = possibleMethods.stream().max(new Comparator<MethodNode>() {
                public int compare(MethodNode m1, MethodNode m2) {
                    Parameter[] p1 = m1.getParameters();
                    Parameter[] p2 = m2.getParameters();
                    int m1Value = calculateArgumentsScore(p1, actualArguments, argIndex);
                    int m2Value = calculateArgumentsScore(p2, actualArguments, argIndex);
                    if (m1Value > m2Value) {
                        return 1;
                    } else if (m1Value < m2Value) {
                        return -1;
                    }
                    return 0;
                }
            }).orElse(null);
            return foundMethod;
        }
        return null;
    }

    private static int calculateArgumentsScore(Parameter[] parameters, ArgumentListExpression arguments, int argIndex) {
        int score = 0;
        int paramCount = parameters.length;
        int expressionsCount = arguments.getExpressions().size();
        int argsCount = expressionsCount;
        if (argIndex >= argsCount) {
            argsCount = argIndex + 1;
        }
        int minCount = Math.min(paramCount, argsCount);
        if (minCount == 0 && paramCount == argsCount) {
            score++;
        }
        for (int i = 0; i < minCount; i++) {
            ClassNode argType = (i < expressionsCount) ? arguments.getExpression(i).getType() : null;
            ClassNode paramType = (i < paramCount) ? parameters[i].getType() : null;
            if (argType != null && paramType != null) {
                if (argType.equals(paramType)) {
                    // equal types are preferred
                    score += 1000;
                } else if (argType.isDerivedFrom(paramType)) {
                    // subtypes are nice, but less important
                    score += 100;
                } else {
                    // if a type doesn't match at all, it's not worth much
                    score++;
                }
            } else if (paramType != null) {
                // extra parameters are like a type not matching
                score++;
            }
        }
        return score;
    }

    public static Range findAddImportRange(ASTNode offsetNode, ASTNodeVisitor astVisitor) {
        ModuleNode moduleNode = (ModuleNode) GroovyASTUtils.getEnclosingNodeOfType(offsetNode, ModuleNode.class,
                astVisitor);
        if (moduleNode == null) {
            return new Range(new Position(0, 0), new Position(0, 0));
        }
        ASTNode afterNode = null;
        if (afterNode == null) {
            List<ImportNode> importNodes = moduleNode.getImports();
            if (importNodes.size() > 0) {
                afterNode = importNodes.get(importNodes.size() - 1);
            }
        }
        if (afterNode == null) {
            afterNode = moduleNode.getPackage();
        }
        if (afterNode == null) {
            return new Range(new Position(0, 0), new Position(0, 0));
        }
        Range nodeRange = GroovyLanguageServerUtils.astNodeToRange(afterNode);
        if (nodeRange == null) {
            return new Range(new Position(0, 0), new Position(0, 0));
        }
        Position position = new Position(nodeRange.getEnd().getLine() + 1, 0);
        return new Range(position, position);
    }
}