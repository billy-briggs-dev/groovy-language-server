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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;

public class CodeActionProvider {
	private static final int MAX_OVERRIDE_METHODS = 3;

	private ASTNodeVisitor ast;

	public CodeActionProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<Either<Command, CodeAction>>> provideCodeActions(CodeActionParams params) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		List<Either<Command, CodeAction>> codeActions = new ArrayList<>();
		URI uri = URI.create(params.getTextDocument().getUri());
		Range range = params.getRange();
		Position position = range.getStart();

		ASTNode node = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		if (node == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		// Find the enclosing class
		ClassNode classNode = findEnclosingClass(node, uri, position);
		if (classNode != null && !classNode.isInterface()) {
			codeActions.addAll(createGetterSetterActions(classNode, uri));
			codeActions.addAll(createConstructorActions(classNode, uri));
			codeActions.addAll(createToStringAction(classNode, uri));
			codeActions.addAll(createEqualsHashCodeActions(classNode, uri));
		}

		if (classNode != null && classNode.isInterface()) {
			// For interfaces, no code generation needed (already abstract)
		} else if (classNode != null) {
			codeActions.addAll(createImplementInterfaceMethodsActions(classNode, uri));
			codeActions.addAll(createOverrideMethodActions(classNode, uri));
		}

		return CompletableFuture.completedFuture(codeActions);
	}

	private ClassNode findEnclosingClass(ASTNode node, URI uri, Position position) {
		// Use GroovyASTUtils to find enclosing class
		ClassNode classNode = (ClassNode) net.prominic.groovyls.compiler.util.GroovyASTUtils
				.getEnclosingNodeOfType(node, ClassNode.class, ast);
		if (classNode != null) {
			return classNode;
		}

		// If the node itself is a ClassNode, return it
		if (node instanceof ClassNode) {
			return (ClassNode) node;
		}

		return null;
	}

	private List<Either<Command, CodeAction>> createGetterSetterActions(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		// Get all fields that don't have getters/setters
		List<FieldNode> fieldsNeedingGetters = new ArrayList<>();
		List<FieldNode> fieldsNeedingSetters = new ArrayList<>();

		for (FieldNode field : classNode.getFields()) {
			if (field.isPrivate() || field.isProtected()) {
				String fieldName = field.getName();
				String getterName = "get" + capitalize(fieldName);
				String setterName = "set" + capitalize(fieldName);

				if (!hasMethod(classNode, getterName)) {
					fieldsNeedingGetters.add(field);
				}
				if (!field.isFinal() && !hasMethod(classNode, setterName)) {
					fieldsNeedingSetters.add(field);
				}
			}
		}

		// Create action for all getters
		if (!fieldsNeedingGetters.isEmpty()) {
			CodeAction action = new CodeAction("Generate Getters for All Fields");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createGettersEdit(classNode, fieldsNeedingGetters, uri));
			actions.add(Either.forRight(action));
		}

		// Create action for all setters
		if (!fieldsNeedingSetters.isEmpty()) {
			CodeAction action = new CodeAction("Generate Setters for All Fields");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createSettersEdit(classNode, fieldsNeedingSetters, uri));
			actions.add(Either.forRight(action));
		}

		// Create action for both
		if (!fieldsNeedingGetters.isEmpty() || !fieldsNeedingSetters.isEmpty()) {
			CodeAction action = new CodeAction("Generate Getters and Setters");
			action.setKind(CodeActionKind.Refactor);
			List<FieldNode> allFields = new ArrayList<>();
			allFields.addAll(fieldsNeedingGetters);
			fieldsNeedingSetters.stream()
					.filter(f -> !allFields.contains(f))
					.forEach(allFields::add);
			action.setEdit(createGettersAndSettersEdit(classNode, fieldsNeedingGetters, fieldsNeedingSetters, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private List<Either<Command, CodeAction>> createConstructorActions(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		// Get all non-static fields
		List<FieldNode> fields = classNode.getFields().stream()
				.filter(f -> !f.isStatic())
				.collect(Collectors.toList());

		if (fields.isEmpty()) {
			return actions;
		}

		// Check if there's already a constructor with all fields
		boolean hasFullConstructor = classNode.getDeclaredConstructors().stream()
				.anyMatch(c -> c.getParameters().length == fields.size());

		if (!hasFullConstructor) {
			CodeAction action = new CodeAction("Generate Constructor");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createConstructorEdit(classNode, fields, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private List<Either<Command, CodeAction>> createToStringAction(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		// Check if toString() already exists
		if (!hasMethod(classNode, "toString")) {
			CodeAction action = new CodeAction("Generate toString()");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createToStringEdit(classNode, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private List<Either<Command, CodeAction>> createEqualsHashCodeActions(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		boolean hasEquals = hasMethod(classNode, "equals");
		boolean hasHashCode = hasMethod(classNode, "hashCode");

		if (!hasEquals || !hasHashCode) {
			CodeAction action = new CodeAction("Generate equals() and hashCode()");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createEqualsHashCodeEdit(classNode, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private List<Either<Command, CodeAction>> createImplementInterfaceMethodsActions(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		// Get all unimplemented interface methods
		List<MethodNode> unimplementedMethods = new ArrayList<>();
		for (ClassNode iface : classNode.getInterfaces()) {
			for (MethodNode method : iface.getMethods()) {
				if (!method.isStatic() && !hasMethodWithSignature(classNode, method)) {
					unimplementedMethods.add(method);
				}
			}
		}

		if (!unimplementedMethods.isEmpty()) {
			CodeAction action = new CodeAction("Implement Interface Methods");
			action.setKind(CodeActionKind.QuickFix);
			action.setEdit(createImplementMethodsEdit(classNode, unimplementedMethods, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private List<Either<Command, CodeAction>> createOverrideMethodActions(ClassNode classNode, URI uri) {
		List<Either<Command, CodeAction>> actions = new ArrayList<>();

		// Get overridable methods from superclass
		List<MethodNode> overridableMethods = new ArrayList<>();
		ClassNode superClass = classNode.getSuperClass();
		if (superClass != null && !superClass.equals(ClassNode.SUPER)) {
			for (MethodNode method : superClass.getMethods()) {
				// Skip constructors (represented as "<init>"), static, final, and private methods
				if (!method.isStatic() && !method.isFinal() && !method.isPrivate() 
						&& !hasMethodWithSignature(classNode, method)
						&& !method.getName().equals("<init>")) {
					overridableMethods.add(method);
				}
			}
		}

		if (!overridableMethods.isEmpty()) {
			CodeAction action = new CodeAction("Override Methods");
			action.setKind(CodeActionKind.Refactor);
			action.setEdit(createOverrideMethodsEdit(classNode, overridableMethods, uri));
			actions.add(Either.forRight(action));
		}

		return actions;
	}

	private boolean hasMethod(ClassNode classNode, String methodName) {
		for (MethodNode method : classNode.getMethods()) {
			if (method.getName().equals(methodName)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasMethodWithSignature(ClassNode classNode, MethodNode targetMethod) {
		for (MethodNode method : classNode.getMethods()) {
			if (method.getName().equals(targetMethod.getName())) {
				Parameter[] params1 = method.getParameters();
				Parameter[] params2 = targetMethod.getParameters();
				if (params1.length == params2.length) {
					boolean match = true;
					for (int i = 0; i < params1.length; i++) {
						if (!params1[i].getType().equals(params2[i].getType())) {
							match = false;
							break;
						}
					}
					if (match) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private WorkspaceEdit createGettersEdit(ClassNode classNode, List<FieldNode> fields, URI uri) {
		StringBuilder code = new StringBuilder();
		for (FieldNode field : fields) {
			code.append("\n    ");
			code.append(generateGetter(field));
			code.append("\n");
		}
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createSettersEdit(ClassNode classNode, List<FieldNode> fields, URI uri) {
		StringBuilder code = new StringBuilder();
		for (FieldNode field : fields) {
			code.append("\n    ");
			code.append(generateSetter(field));
			code.append("\n");
		}
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createGettersAndSettersEdit(ClassNode classNode, List<FieldNode> getterFields,
			List<FieldNode> setterFields, URI uri) {
		StringBuilder code = new StringBuilder();
		for (FieldNode field : getterFields) {
			code.append("\n    ");
			code.append(generateGetter(field));
			code.append("\n");
		}
		for (FieldNode field : setterFields) {
			code.append("\n    ");
			code.append(generateSetter(field));
			code.append("\n");
		}
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createConstructorEdit(ClassNode classNode, List<FieldNode> fields, URI uri) {
		StringBuilder code = new StringBuilder();
		code.append("\n    ");
		code.append(generateConstructor(classNode, fields));
		code.append("\n");
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createToStringEdit(ClassNode classNode, URI uri) {
		StringBuilder code = new StringBuilder();
		code.append("\n    ");
		code.append(generateToString(classNode));
		code.append("\n");
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createEqualsHashCodeEdit(ClassNode classNode, URI uri) {
		StringBuilder code = new StringBuilder();
		code.append("\n    ");
		code.append(generateEquals(classNode));
		code.append("\n\n    ");
		code.append(generateHashCode(classNode));
		code.append("\n");
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createImplementMethodsEdit(ClassNode classNode, List<MethodNode> methods, URI uri) {
		StringBuilder code = new StringBuilder();
		for (MethodNode method : methods) {
			code.append("\n    @Override\n    ");
			code.append(generateMethodStub(method));
			code.append("\n");
		}
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createOverrideMethodsEdit(ClassNode classNode, List<MethodNode> methods, URI uri) {
		StringBuilder code = new StringBuilder();
		// Limit to first few methods to avoid overwhelming the user
		int count = Math.min(methods.size(), MAX_OVERRIDE_METHODS);
		for (int i = 0; i < count; i++) {
			MethodNode method = methods.get(i);
			code.append("\n    @Override\n    ");
			code.append(generateMethodStub(method));
			code.append("\n");
		}
		return createInsertEdit(classNode, code.toString(), uri);
	}

	private WorkspaceEdit createInsertEdit(ClassNode classNode, String code, URI uri) {
		// Find the insertion point (end of class, before closing brace)
		int lastLine = classNode.getLastLineNumber() - 1;
		Position position = new Position(lastLine, 0);
		Range range = new Range(position, position);

		TextEdit textEdit = new TextEdit(range, code);
		Map<String, List<TextEdit>> changes = new HashMap<>();
		changes.put(uri.toString(), Collections.singletonList(textEdit));

		WorkspaceEdit edit = new WorkspaceEdit();
		edit.setChanges(changes);
		return edit;
	}

	private String generateGetter(FieldNode field) {
		String fieldName = field.getName();
		String methodName = "get" + capitalize(fieldName);
		return String.format("def %s() {\n        return %s\n    }", methodName, fieldName);
	}

	private String generateSetter(FieldNode field) {
		String fieldName = field.getName();
		String methodName = "set" + capitalize(fieldName);
		String typeName = field.getType().getNameWithoutPackage();
		return String.format("void %s(%s %s) {\n        this.%s = %s\n    }", 
				methodName, typeName, fieldName, fieldName, fieldName);
	}

	private String generateConstructor(ClassNode classNode, List<FieldNode> fields) {
		String className = classNode.getNameWithoutPackage();
		StringBuilder params = new StringBuilder();
		StringBuilder body = new StringBuilder();

		for (int i = 0; i < fields.size(); i++) {
			FieldNode field = fields.get(i);
			String fieldName = field.getName();
			String typeName = field.getType().getNameWithoutPackage();

			if (i > 0) {
				params.append(", ");
			}
			params.append(typeName).append(" ").append(fieldName);
			body.append("\n        this.").append(fieldName).append(" = ").append(fieldName);
		}

		return String.format("%s(%s) {%s\n    }", className, params.toString(), body.toString());
	}

	private String generateToString(ClassNode classNode) {
		List<FieldNode> fields = classNode.getFields().stream()
				.filter(f -> !f.isStatic())
				.collect(Collectors.toList());

		StringBuilder body = new StringBuilder();
		body.append("\"").append(classNode.getNameWithoutPackage()).append("(");

		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				body.append(", ");
			}
			String fieldName = fields.get(i).getName();
			body.append(fieldName).append("=${").append(fieldName).append("}");
		}
		body.append(")\"");

		return String.format("@Override\n    String toString() {\n        return %s\n    }", body.toString());
	}

	private String generateEquals(ClassNode classNode) {
		List<FieldNode> fields = classNode.getFields().stream()
				.filter(f -> !f.isStatic())
				.collect(Collectors.toList());

		StringBuilder body = new StringBuilder();
		// Use Groovy's is() method for identity comparison (checks if same object reference)
		body.append("if (this.is(other)) return true\n");
		body.append("        if (other == null || getClass() != other.getClass()) return false\n");
		body.append("        ").append(classNode.getNameWithoutPackage()).append(" that = (")
				.append(classNode.getNameWithoutPackage()).append(") other\n");

		for (FieldNode field : fields) {
			String fieldName = field.getName();
			body.append("        if (").append(fieldName).append(" != that.").append(fieldName)
					.append(") return false\n");
		}
		body.append("        return true");

		return String.format("@Override\n    boolean equals(Object other) {\n        %s\n    }", body.toString());
	}

	private String generateHashCode(ClassNode classNode) {
		List<FieldNode> fields = classNode.getFields().stream()
				.filter(f -> !f.isStatic())
				.collect(Collectors.toList());

		StringBuilder body = new StringBuilder();
		// Use 17 as initial value (common prime for hash functions)
		body.append("int result = 17\n");

		for (FieldNode field : fields) {
			String fieldName = field.getName();
			// Use 31 as multiplier (common prime for hash functions)
			body.append("        result = 31 * result + (").append(fieldName)
					.append(" != null ? ").append(fieldName).append(".hashCode() : 0)\n");
		}
		body.append("        return result");

		return String.format("@Override\n    int hashCode() {\n        %s\n    }", body.toString());
	}

	private String generateMethodStub(MethodNode method) {
		String methodName = method.getName();
		String returnType = method.getReturnType().getNameWithoutPackage();
		
		StringBuilder params = new StringBuilder();
		Parameter[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			if (i > 0) {
				params.append(", ");
			}
			params.append(parameters[i].getType().getNameWithoutPackage())
					.append(" ")
					.append(parameters[i].getName());
		}

		String returnStmt = "";
		if (!returnType.equals("void")) {
			returnStmt = "\n        return null";
		}

		return String.format("%s %s(%s) {%s\n    }", returnType, methodName, params.toString(), returnStmt);
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
