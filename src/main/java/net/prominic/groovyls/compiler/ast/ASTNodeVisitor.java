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
package net.prominic.groovyls.compiler.ast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.RangeExpression;
import org.codehaus.groovy.ast.expr.SpreadExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.UnaryMinusExpression;
import org.codehaus.groovy.ast.expr.UnaryPlusExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.BreakStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ContinueStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import java.lang.reflect.Modifier;

import net.prominic.groovyls.util.GroovyLanguageServerUtils;
import net.prominic.lsp.utils.Positions;
import net.prominic.lsp.utils.Ranges;

public class ASTNodeVisitor extends ClassCodeVisitorSupport {
	private static final Pattern METACLASS_METHOD_PATTERN = Pattern
			.compile("([A-Za-z_][\\w\\.]*)\\.metaClass\\.([A-Za-z_][\\w]*)\\s*=\\s*\\{");

	private class ASTLookupKey {
		public ASTLookupKey(ASTNode node) {
			this.node = node;
		}

		private ASTNode node;

		@Override
		public boolean equals(Object o) {
			// some ASTNode subclasses, like ClassNode, override equals() with
			// comparisons that are not strict. we need strict.
			ASTLookupKey other = (ASTLookupKey) o;
			return node == other.node;
		}

		@Override
		public int hashCode() {
			return node.hashCode();
		}
	}

	private class ASTNodeLookupData {
		public ASTNode parent;
		public URI uri;
	}

	private SourceUnit sourceUnit;

	@Override
	protected SourceUnit getSourceUnit() {
		return sourceUnit;
	}

	private Stack<ASTNode> stack = new Stack<>();
	private Map<URI, List<ASTNode>> nodesByURI = new HashMap<>();
	private Map<URI, List<ClassNode>> classNodesByURI = new HashMap<>();
	private Map<ASTLookupKey, ASTNodeLookupData> lookup = new HashMap<>();
	private Map<String, Map<String, MethodNode>> metaClassMethodsByType = new HashMap<>();
	private Map<String, Map<String, PropertyNode>> metaClassPropertiesByType = new HashMap<>();
	private Map<URI, Map<String, Map<String, MethodNode>>> metaClassMethodsByURI = new HashMap<>();
	private Map<URI, Map<String, Map<String, PropertyNode>>> metaClassPropertiesByURI = new HashMap<>();
	private Map<String, List<MethodNode>> pendingCategoryMethodsByTarget = new HashMap<>();

	private static final List<String> DELEGATE_ANNOTATIONS = List.of("Delegate", "groovy.lang.Delegate");
	private static final List<String> MIXIN_ANNOTATIONS = List.of("Mixin", "groovy.lang.Mixin");
	private static final List<String> CATEGORY_ANNOTATIONS = List.of("Category", "groovy.lang.Category");
	private static final List<String> CANONICAL_ANNOTATIONS = List.of("Canonical", "groovy.transform.Canonical");
	private static final List<String> IMMUTABLE_ANNOTATIONS = List.of("Immutable", "groovy.transform.Immutable");
	private static final List<String> BUILDER_ANNOTATIONS = List.of("Builder", "groovy.transform.builder.Builder");

	private void pushASTNode(ASTNode node) {
		boolean isSynthetic = false;
		if (node instanceof AnnotatedNode) {
			AnnotatedNode annotatedNode = (AnnotatedNode) node;
			isSynthetic = annotatedNode.isSynthetic();
		}
		if (!isSynthetic) {
			URI uri = sourceUnit.getSource().getURI();
			nodesByURI.get(uri).add(node);

			ASTNodeLookupData data = new ASTNodeLookupData();
			data.uri = uri;
			if (stack.size() > 0) {
				data.parent = stack.lastElement();
			}
			lookup.put(new ASTLookupKey(node), data);
		}

		stack.add(node);
	}

	private void popASTNode() {
		stack.pop();
	}

	public List<ClassNode> getClassNodes() {
		List<ClassNode> result = new ArrayList<>();
		for (List<ClassNode> nodes : classNodesByURI.values()) {
			result.addAll(nodes);
		}
		return result;
	}

	public List<ASTNode> getNodes() {
		List<ASTNode> result = new ArrayList<>();
		for (List<ASTNode> nodes : nodesByURI.values()) {
			result.addAll(nodes);
		}
		return result;
	}

	public List<ASTNode> getNodes(URI uri) {
		List<ASTNode> nodes = nodesByURI.get(uri);
		if (nodes == null) {
			return Collections.emptyList();
		}
		return nodes;
	}

	public List<MethodNode> getMetaClassMethods(ClassNode classNode) {
		if (classNode == null) {
			return Collections.emptyList();
		}
		Map<String, MethodNode> methods = metaClassMethodsByType.get(classNode.getName());
		if (methods == null && classNode.getNameWithoutPackage() != null) {
			methods = metaClassMethodsByType.get(classNode.getNameWithoutPackage());
		}
		if (methods == null) {
			return Collections.emptyList();
		}
		return new ArrayList<>(methods.values());
	}

	public List<PropertyNode> getMetaClassProperties(ClassNode classNode) {
		if (classNode == null) {
			return Collections.emptyList();
		}
		Map<String, PropertyNode> props = metaClassPropertiesByType.get(classNode.getName());
		if (props == null && classNode.getNameWithoutPackage() != null) {
			props = metaClassPropertiesByType.get(classNode.getNameWithoutPackage());
		}
		if (props == null) {
			return Collections.emptyList();
		}
		return new ArrayList<>(props.values());
	}

	public ASTNode getNodeAtLineAndColumn(URI uri, int line, int column) {
		Position position = new Position(line, column);
		Map<ASTNode, Range> nodeToRange = new HashMap<>();
		List<ASTNode> nodes = nodesByURI.get(uri);
		if (nodes == null) {
			return null;
		}
		List<ASTNode> foundNodes = nodes.stream().filter(node -> {
			if (node.getLineNumber() == -1) {
				// can't be the offset node if it has no position
				// also, do this first because it's the fastest comparison
				return false;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(node);
			if (range == null) {
				return false;
			}
			boolean result = Ranges.contains(range, position);
			if (result) {
				// save the range object to avoid creating it again when we
				// sort the nodes
				nodeToRange.put(node, range);
			}
			return result;
		}).sorted((n1, n2) -> {
			int result = Positions.COMPARATOR.reversed().compare(nodeToRange.get(n1).getStart(),
					nodeToRange.get(n2).getStart());
			if (result != 0) {
				return result;
			}
			result = Positions.COMPARATOR.compare(nodeToRange.get(n1).getEnd(), nodeToRange.get(n2).getEnd());
			if (result != 0) {
				return result;
			}
			// n1 and n2 have the same range
			if (contains(n1, n2)) {
				if (n1 instanceof ClassNode && n2 instanceof ConstructorNode) {
					return -1;
				}
				return 1;
			} else if (contains(n2, n1)) {
				if (n2 instanceof ClassNode && n1 instanceof ConstructorNode) {
					return 1;
				}
				return -1;
			}
			return 0;
		}).collect(Collectors.toList());
		if (foundNodes.size() == 0) {
			return null;
		}
		return foundNodes.get(0);
	}

	public ASTNode getParent(ASTNode child) {
		if (child == null) {
			return null;
		}
		ASTNodeLookupData data = lookup.get(new ASTLookupKey(child));
		if (data == null) {
			return null;
		}
		return data.parent;
	}

	public boolean contains(ASTNode ancestor, ASTNode descendant) {
		ASTNode current = getParent(descendant);
		while (current != null) {
			if (current.equals(ancestor)) {
				return true;
			}
			current = getParent(current);
		}
		return false;
	}

	public URI getURI(ASTNode node) {
		ASTNodeLookupData data = lookup.get(new ASTLookupKey(node));
		if (data == null) {
			return null;
		}
		return data.uri;
	}

	public void visitCompilationUnit(CompilationUnit unit) {
		nodesByURI.clear();
		classNodesByURI.clear();
		lookup.clear();
		metaClassMethodsByType.clear();
		metaClassPropertiesByType.clear();
		metaClassMethodsByURI.clear();
		metaClassPropertiesByURI.clear();
		pendingCategoryMethodsByTarget.clear();
		unit.iterator().forEachRemaining(sourceUnit -> {
			visitSourceUnit(sourceUnit);
		});
	}

	public void visitCompilationUnit(CompilationUnit unit, Collection<URI> uris) {
		uris.forEach(uri -> {
			// clear all old nodes so that they may be replaced
			List<ASTNode> nodes = nodesByURI.remove(uri);
			if (nodes != null) {
				nodes.forEach(node -> {
					lookup.remove(new ASTLookupKey(node));
				});
			}
			classNodesByURI.remove(uri);
			removeMetaClassEntriesForUri(uri);
		});
		pendingCategoryMethodsByTarget.clear();
		unit.iterator().forEachRemaining(sourceUnit -> {
			URI uri = sourceUnit.getSource().getURI();
			if (!uris.contains(uri)) {
				return;
			}
			visitSourceUnit(sourceUnit);
		});
	}

	private void removeMetaClassEntriesForUri(URI uri) {
		Map<String, Map<String, MethodNode>> methodsByType = metaClassMethodsByURI.remove(uri);
		if (methodsByType != null) {
			methodsByType.forEach((typeName, methods) -> {
				Map<String, MethodNode> existing = metaClassMethodsByType.get(typeName);
				if (existing != null) {
					methods.keySet().forEach(existing::remove);
					if (existing.isEmpty()) {
						metaClassMethodsByType.remove(typeName);
					}
				}
			});
		}
		Map<String, Map<String, PropertyNode>> propsByType = metaClassPropertiesByURI.remove(uri);
		if (propsByType != null) {
			propsByType.forEach((typeName, props) -> {
				Map<String, PropertyNode> existing = metaClassPropertiesByType.get(typeName);
				if (existing != null) {
					props.keySet().forEach(existing::remove);
					if (existing.isEmpty()) {
						metaClassPropertiesByType.remove(typeName);
					}
				}
			});
		}
	}

	public void visitSourceUnit(SourceUnit unit) {
		sourceUnit = unit;
		URI uri = sourceUnit.getSource().getURI();
		nodesByURI.put(uri, new ArrayList<>());
		classNodesByURI.put(uri, new ArrayList<>());
		stack.clear();
		ModuleNode moduleNode = unit.getAST();
		if (moduleNode != null) {
			visitModule(moduleNode);
		}
		captureMetaClassAssignmentsFromSource();
		sourceUnit = null;
		stack.clear();
	}

	private void captureMetaClassAssignmentsFromSource() {
		if (sourceUnit == null || sourceUnit.getSource() == null) {
			return;
		}
		String text = readSourceText();
		if (text == null || text.isBlank()) {
			return;
		}
		Matcher matcher = METACLASS_METHOD_PATTERN.matcher(text);
		while (matcher.find()) {
			String className = matcher.group(1);
			String methodName = matcher.group(2);
			ClassNode targetType = null;
			for (ClassNode classNode : getClassNodes()) {
				if (className.equals(classNode.getName())
						|| className.equals(classNode.getNameWithoutPackage())) {
					targetType = classNode;
					break;
				}
			}
			if (targetType == null) {
				targetType = new ClassNode(className, 0, ClassHelper.OBJECT_TYPE);
			}
			MethodNode methodNode = new MethodNode(methodName, 0, ClassHelper.dynamicType(), new Parameter[0],
					new ClassNode[0], null);
			methodNode.setDeclaringClass(targetType);
			if (targetType.getMethods(methodName).isEmpty()) {
				targetType.addMethod(methodNode);
			}
			metaClassMethodsByType.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(methodName,
					methodNode);
			String simpleName = targetType.getNameWithoutPackage();
			if (simpleName != null && !simpleName.equals(targetType.getName())) {
				metaClassMethodsByType.computeIfAbsent(simpleName, key -> new HashMap<>()).put(methodName, methodNode);
			}
			URI uri = sourceUnit.getSource().getURI();
			if (uri != null) {
				metaClassMethodsByURI.computeIfAbsent(uri, key -> new HashMap<>())
						.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(methodName, methodNode);
				if (simpleName != null && !simpleName.equals(targetType.getName())) {
					metaClassMethodsByURI.computeIfAbsent(uri, key -> new HashMap<>())
							.computeIfAbsent(simpleName, key -> new HashMap<>()).put(methodName, methodNode);
				}
			}
		}
	}

	private String readSourceText() {
		try (Reader reader = sourceUnit.getSource().getReader();
				BufferedReader buffered = new BufferedReader(reader)) {
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[4096];
			int read;
			while ((read = buffered.read(buffer)) != -1) {
				builder.append(buffer, 0, read);
			}
			return builder.toString();
		} catch (IOException e) {
			return null;
		}
	}

	public void visitModule(ModuleNode node) {
		pushASTNode(node);
		try {
			node.getClasses().forEach(classInUnit -> {
				visitClass(classInUnit);
			});
			if (node.getStatementBlock() != null) {
				node.getStatementBlock().visit(this);
			}
		} finally {
			popASTNode();
		}
	}

	// GroovyClassVisitor

	public void visitClass(ClassNode node) {
		URI uri = sourceUnit.getSource().getURI();
		classNodesByURI.get(uri).add(node);
		pushASTNode(node);
		try {
			applyPendingCategoryMethods(node);
			applyAstTransformations(node);
			ClassNode unresolvedSuperClass = node.getUnresolvedSuperClass();
			if (unresolvedSuperClass != null && unresolvedSuperClass.getLineNumber() != -1) {
				pushASTNode(unresolvedSuperClass);
				popASTNode();
			}
			for (ClassNode unresolvedInterface : node.getUnresolvedInterfaces()) {
				if (unresolvedInterface.getLineNumber() == -1) {
					continue;
				}
				pushASTNode(unresolvedInterface);
				popASTNode();
			}
			super.visitClass(node);
		} finally {
			popASTNode();
		}
	}

	private void applyAstTransformations(ClassNode node) {
		applyDelegateTransformations(node);
		applyMixinTransformations(node);
		applyCategoryTransformations(node);
		applyCanonicalTransformations(node);
		applyImmutableTransformations(node);
		applyBuilderTransformations(node);
	}

	private void applyDelegateTransformations(ClassNode node) {
		node.getFields().forEach(field -> {
			if (hasAnyAnnotation(field, DELEGATE_ANNOTATIONS)) {
				addDelegatedMembers(node, field.getType());
			}
		});
		node.getProperties().forEach(prop -> {
			if (hasAnyAnnotation(prop, DELEGATE_ANNOTATIONS)) {
				addDelegatedMembers(node, prop.getType());
			}
		});
	}

	private void applyMixinTransformations(ClassNode node) {
		List<AnnotationNode> annotations = getAnnotationsByName(node, MIXIN_ANNOTATIONS);
		for (AnnotationNode annotation : annotations) {
			for (ClassNode mixinType : resolveAnnotationClassNodes(annotation)) {
				addDelegatedMembers(node, mixinType);
			}
		}
	}

	private void applyCategoryTransformations(ClassNode node) {
		List<AnnotationNode> annotations = getAnnotationsByName(node, CATEGORY_ANNOTATIONS);
		if (annotations.isEmpty()) {
			return;
		}
		List<MethodNode> categoryMethods = node.getMethods();
		for (AnnotationNode annotation : annotations) {
			for (ClassNode targetType : resolveAnnotationClassNodes(annotation)) {
				ClassNode existingTarget = findClassNodeByName(targetType.getName());
				if (existingTarget != null) {
					addCategoryMethods(existingTarget, categoryMethods);
				} else {
					pendingCategoryMethodsByTarget
							.computeIfAbsent(targetType.getName(), key -> new ArrayList<>())
							.addAll(categoryMethods);
					String simple = targetType.getNameWithoutPackage();
					if (simple != null && !simple.equals(targetType.getName())) {
						pendingCategoryMethodsByTarget
								.computeIfAbsent(simple, key -> new ArrayList<>())
								.addAll(categoryMethods);
					}
				}
			}
		}
	}

	private void applyCanonicalTransformations(ClassNode node) {
		if (!hasAnyAnnotation(node, CANONICAL_ANNOTATIONS)) {
			return;
		}
		addTupleConstructorIfMissing(node);
		addMethodIfMissing(node, "toString", ClassHelper.STRING_TYPE, new Parameter[0], 0);
		addMethodIfMissing(node, "hashCode", ClassHelper.int_TYPE, new Parameter[0], 0);
		addMethodIfMissing(node, "equals", ClassHelper.boolean_TYPE,
				new Parameter[] { new Parameter(ClassHelper.OBJECT_TYPE, "other") }, 0);
	}

	private void applyImmutableTransformations(ClassNode node) {
		if (!hasAnyAnnotation(node, IMMUTABLE_ANNOTATIONS)) {
			return;
		}
		applyCanonicalTransformations(node);
		addTupleConstructorIfMissing(node);
		addMethodIfMissing(node, "copyWith", node,
				new Parameter[] { new Parameter(ClassHelper.MAP_TYPE, "values") }, 0);
		addMethodIfMissing(node, "toMap", ClassHelper.MAP_TYPE, new Parameter[0], 0);
	}

	private void addTupleConstructorIfMissing(ClassNode node) {
		if (node == null) {
			return;
		}
		List<PropertyNode> props = node.getProperties();
		if (props == null || props.isEmpty()) {
			return;
		}
		Parameter[] parameters = new Parameter[props.size()];
		for (int i = 0; i < props.size(); i++) {
			PropertyNode prop = props.get(i);
			parameters[i] = new Parameter(prop.getType(), prop.getName());
		}
		for (ConstructorNode constructor : node.getDeclaredConstructors()) {
			if (sameParameterTypes(constructor.getParameters(), parameters)) {
				return;
			}
		}
		ConstructorNode ctor = new ConstructorNode(Modifier.PUBLIC, parameters,
				ClassNode.EMPTY_ARRAY, new BlockStatement());
		ctor.setSynthetic(true);
		node.addConstructor(ctor);
		ClassNode redirected = node.redirect();
		if (redirected != null && redirected != node) {
			boolean exists = redirected.getDeclaredConstructors().stream()
					.anyMatch(existing -> sameParameterTypes(existing.getParameters(), parameters));
			if (!exists) {
				ConstructorNode redirectedCtor = new ConstructorNode(Modifier.PUBLIC, parameters,
						ClassNode.EMPTY_ARRAY, new BlockStatement());
				redirectedCtor.setSynthetic(true);
				redirected.addConstructor(redirectedCtor);
			}
		}
	}

	private void applyBuilderTransformations(ClassNode node) {
		if (!hasAnyAnnotation(node, BUILDER_ANNOTATIONS)) {
			return;
		}
		ClassNode builderType = new ClassNode(node.getName() + "Builder", Modifier.PUBLIC,
				ClassHelper.OBJECT_TYPE);
		addMethodIfMissing(node, "builder", builderType, new Parameter[0], Modifier.STATIC);
	}

	private void applyPendingCategoryMethods(ClassNode node) {
		List<MethodNode> pending = pendingCategoryMethodsByTarget.remove(node.getName());
		if (pending == null) {
			pending = pendingCategoryMethodsByTarget.remove(node.getNameWithoutPackage());
		}
		if (pending == null || pending.isEmpty()) {
			return;
		}
		addCategoryMethods(node, pending);
	}

	private void addDelegatedMembers(ClassNode target, ClassNode source) {
		if (target == null || source == null) {
			return;
		}
		for (MethodNode method : source.getMethods()) {
			if (method.isStatic() || isIgnoredDelegateMethod(method)) {
				continue;
			}
			MethodNode synthetic = cloneMethod(method, target, method.getParameters(), method.getModifiers());
			addMethodIfMissing(target, synthetic);
		}
		for (PropertyNode prop : source.getProperties()) {
			addPropertyIfMissing(target, prop);
		}
		for (FieldNode field : source.getFields()) {
			addFieldAsPropertyIfMissing(target, field);
		}
	}

	private void addCategoryMethods(ClassNode target, List<MethodNode> categoryMethods) {
		if (target == null || categoryMethods == null) {
			return;
		}
		for (MethodNode method : categoryMethods) {
			if (method.getName().equals("<init>")) {
				continue;
			}
			Parameter[] params = method.getParameters();
			int modifiers = method.getModifiers() & ~Modifier.STATIC;
			if (method.isStatic() && params.length > 0 && isParameterTargetMatch(params[0], target)) {
				Parameter[] trimmed = new Parameter[params.length - 1];
				System.arraycopy(params, 1, trimmed, 0, trimmed.length);
				params = trimmed;
			}
			MethodNode synthetic = cloneMethod(method, target, params, modifiers);
			addMethodIfMissing(target, synthetic);
		}
	}

	private boolean isIgnoredDelegateMethod(MethodNode method) {
		ClassNode declaring = method.getDeclaringClass();
		if (declaring == null) {
			return false;
		}
		String name = declaring.getName();
		return "java.lang.Object".equals(name)
				|| "groovy.lang.GroovyObject".equals(name)
				|| "groovy.lang.GroovyObjectSupport".equals(name);
	}

	private MethodNode cloneMethod(MethodNode source, ClassNode target, Parameter[] parameters, int modifiers) {
		Parameter[] cloned = new Parameter[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			Parameter param = parameters[i];
			Parameter copy = new Parameter(param.getType(), param.getName());
			copy.setInitialExpression(param.getInitialExpression());
			cloned[i] = copy;
		}
		MethodNode synthetic = new MethodNode(source.getName(), modifiers, source.getReturnType(),
				cloned, new ClassNode[0], null);
		synthetic.setDeclaringClass(target);
		synthetic.setSynthetic(true);
		return synthetic;
	}

	private void addMethodIfMissing(ClassNode target, String name, ClassNode returnType,
			Parameter[] parameters, int modifiers) {
		MethodNode synthetic = new MethodNode(name, modifiers, returnType, parameters,
				new ClassNode[0], null);
		synthetic.setDeclaringClass(target);
		synthetic.setSynthetic(true);
		addMethodIfMissing(target, synthetic);
	}

	private void addMethodIfMissing(ClassNode target, MethodNode method) {
		addMethodIfMissingInternal(target, method);
		ClassNode redirected = target != null ? target.redirect() : null;
		if (redirected != null && redirected != target) {
			MethodNode copy = cloneMethod(method, redirected, method.getParameters(), method.getModifiers());
			addMethodIfMissingInternal(redirected, copy);
		}
	}

	private void addMethodIfMissingInternal(ClassNode target, MethodNode method) {
		if (target == null || method == null) {
			return;
		}
		if (hasMethodSignature(target, method)) {
			return;
		}
		target.addMethod(method);
	}

	private boolean hasMethodSignature(ClassNode target, MethodNode candidate) {
		for (MethodNode existing : target.getMethods(candidate.getName())) {
			if (sameParameterTypes(existing.getParameters(), candidate.getParameters())) {
				return true;
			}
		}
		return false;
	}

	private boolean sameParameterTypes(Parameter[] a, Parameter[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			ClassNode aType = a[i].getType();
			ClassNode bType = b[i].getType();
			String aName = aType != null ? aType.getName() : null;
			String bName = bType != null ? bType.getName() : null;
			if (aName == null || bName == null || !aName.equals(bName)) {
				return false;
			}
		}
		return true;
	}

	private void addPropertyIfMissing(ClassNode target, PropertyNode prop) {
		addPropertyIfMissingInternal(target, prop);
		ClassNode redirected = target != null ? target.redirect() : null;
		if (redirected != null && redirected != target) {
			addPropertyIfMissingInternal(redirected, prop);
		}
	}

	private void addPropertyIfMissingInternal(ClassNode target, PropertyNode prop) {
		if (target == null || prop == null) {
			return;
		}
		if (target.getProperty(prop.getName()) != null || target.getField(prop.getName()) != null) {
			return;
		}
		PropertyNode synthetic = new PropertyNode(prop.getName(), prop.getModifiers(), prop.getType(),
				target, null, null, null);
		synthetic.setDeclaringClass(target);
		synthetic.setSynthetic(true);
		target.addProperty(synthetic);
	}

	private void addFieldAsPropertyIfMissing(ClassNode target, FieldNode field) {
		addFieldAsPropertyIfMissingInternal(target, field);
		ClassNode redirected = target != null ? target.redirect() : null;
		if (redirected != null && redirected != target) {
			addFieldAsPropertyIfMissingInternal(redirected, field);
		}
	}

	private void addFieldAsPropertyIfMissingInternal(ClassNode target, FieldNode field) {
		if (target == null || field == null) {
			return;
		}
		if (target.getProperty(field.getName()) != null || target.getField(field.getName()) != null) {
			return;
		}
		PropertyNode synthetic = new PropertyNode(field.getName(), field.getModifiers(), field.getType(),
				target, null, null, null);
		synthetic.setDeclaringClass(target);
		synthetic.setSynthetic(true);
		target.addProperty(synthetic);
	}

	private boolean isParameterTargetMatch(Parameter param, ClassNode target) {
		ClassNode type = param.getType();
		if (type == null || target == null) {
			return false;
		}
		String name = type.getName();
		return name.equals(target.getName())
				|| (target.getNameWithoutPackage() != null && name.equals(target.getNameWithoutPackage()));
	}

	private boolean hasAnyAnnotation(AnnotatedNode node, List<String> names) {
		for (AnnotationNode annotation : node.getAnnotations()) {
			String fullName = annotation.getClassNode().getName();
			String simple = annotation.getClassNode().getNameWithoutPackage();
			if (names.contains(fullName) || (simple != null && names.contains(simple))) {
				return true;
			}
		}
		return false;
	}

	private List<AnnotationNode> getAnnotationsByName(AnnotatedNode node, List<String> names) {
		List<AnnotationNode> result = new ArrayList<>();
		for (AnnotationNode annotation : node.getAnnotations()) {
			String fullName = annotation.getClassNode().getName();
			String simple = annotation.getClassNode().getNameWithoutPackage();
			if (names.contains(fullName) || (simple != null && names.contains(simple))) {
				result.add(annotation);
			}
		}
		return result;
	}

	private List<ClassNode> resolveAnnotationClassNodes(AnnotationNode annotation) {
		Expression value = annotation.getMember("value");
		if (value == null) {
			return Collections.emptyList();
		}
		List<ClassNode> result = new ArrayList<>();
		if (value instanceof ListExpression) {
			ListExpression list = (ListExpression) value;
			for (Expression expr : list.getExpressions()) {
				ClassNode resolved = resolveClassNodeFromExpression(expr);
				if (resolved != null) {
					result.add(resolved);
				}
			}
		} else {
			ClassNode resolved = resolveClassNodeFromExpression(value);
			if (resolved != null) {
				result.add(resolved);
			}
		}
		return result;
	}

	private ClassNode resolveClassNodeFromExpression(Expression expr) {
		if (expr instanceof ClassExpression) {
			ClassNode exprType = ((ClassExpression) expr).getType();
			if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type
					&& exprType.getGenericsTypes() != null && exprType.getGenericsTypes().length > 0) {
				return exprType.getGenericsTypes()[0].getType();
			}
			if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type) {
				return resolveClassNodeByName(expr.getText());
			}
			return exprType;
		}
		if (expr instanceof ConstantExpression) {
			return resolveClassNodeByName(((ConstantExpression) expr).getText());
		}
		if (expr instanceof VariableExpression) {
			return resolveClassNodeByName(((VariableExpression) expr).getName());
		}
		return null;
	}

	private ClassNode findClassNodeByName(String className) {
		if (className == null) {
			return null;
		}
		for (ClassNode classNode : getClassNodes()) {
			if (className.equals(classNode.getName())
					|| className.equals(classNode.getNameWithoutPackage())) {
				return classNode;
			}
		}
		return null;
	}

	@Override
	public void visitImports(ModuleNode node) {
		if (node != null) {
			for (ImportNode importNode : node.getImports()) {
				pushASTNode(importNode);
				visitAnnotations(importNode);
				importNode.visit(this);
				popASTNode();
			}
			for (ImportNode importStarNode : node.getStarImports()) {
				pushASTNode(importStarNode);
				visitAnnotations(importStarNode);
				importStarNode.visit(this);
				popASTNode();
			}
			for (ImportNode importStaticNode : node.getStaticImports().values()) {
				pushASTNode(importStaticNode);
				visitAnnotations(importStaticNode);
				importStaticNode.visit(this);
				popASTNode();
			}
			for (ImportNode importStaticStarNode : node.getStaticStarImports().values()) {
				pushASTNode(importStaticStarNode);
				visitAnnotations(importStaticStarNode);
				importStaticStarNode.visit(this);
				popASTNode();
			}
		}
	}

	public void visitConstructor(ConstructorNode node) {
		pushASTNode(node);
		try {
			super.visitConstructor(node);
			for (Parameter parameter : node.getParameters()) {
				visitParameter(parameter);
			}
		} finally {
			popASTNode();
		}
	}

	public void visitMethod(MethodNode node) {
		pushASTNode(node);
		try {
			super.visitMethod(node);
			for (Parameter parameter : node.getParameters()) {
				visitParameter(parameter);
			}
		} finally {
			popASTNode();
		}
	}

	protected void visitParameter(Parameter node) {
		pushASTNode(node);
		try {
		} finally {
			popASTNode();
		}
	}

	public void visitField(FieldNode node) {
		pushASTNode(node);
		try {
			super.visitField(node);
		} finally {
			popASTNode();
		}
	}

	public void visitProperty(PropertyNode node) {
		pushASTNode(node);
		try {
			super.visitProperty(node);
		} finally {
			popASTNode();
		}
	}

	// GroovyCodeVisitor

	public void visitBlockStatement(BlockStatement node) {
		pushASTNode(node);
		try {
			super.visitBlockStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitForLoop(ForStatement node) {
		pushASTNode(node);
		try {
			super.visitForLoop(node);
		} finally {
			popASTNode();
		}
	}

	public void visitWhileLoop(WhileStatement node) {
		pushASTNode(node);
		try {
			super.visitWhileLoop(node);
		} finally {
			popASTNode();
		}
	}

	public void visitDoWhileLoop(DoWhileStatement node) {
		pushASTNode(node);
		try {
			super.visitDoWhileLoop(node);
		} finally {
			popASTNode();
		}
	}

	public void visitIfElse(IfStatement node) {
		pushASTNode(node);
		try {
			super.visitIfElse(node);
		} finally {
			popASTNode();
		}
	}

	public void visitExpressionStatement(ExpressionStatement node) {
		pushASTNode(node);
		try {
			super.visitExpressionStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitReturnStatement(ReturnStatement node) {
		pushASTNode(node);
		try {
			super.visitReturnStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitAssertStatement(AssertStatement node) {
		pushASTNode(node);
		try {
			super.visitAssertStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitTryCatchFinally(TryCatchStatement node) {
		pushASTNode(node);
		try {
			super.visitTryCatchFinally(node);
		} finally {
			popASTNode();
		}
	}

	public void visitEmptyStatement(EmptyStatement node) {
		pushASTNode(node);
		try {
			super.visitEmptyStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitSwitch(SwitchStatement node) {
		pushASTNode(node);
		try {
			super.visitSwitch(node);
		} finally {
			popASTNode();
		}
	}

	public void visitCaseStatement(CaseStatement node) {
		pushASTNode(node);
		try {
			super.visitCaseStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitBreakStatement(BreakStatement node) {
		pushASTNode(node);
		try {
			super.visitBreakStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitContinueStatement(ContinueStatement node) {
		pushASTNode(node);
		try {
			super.visitContinueStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitSynchronizedStatement(SynchronizedStatement node) {
		pushASTNode(node);
		try {
			super.visitSynchronizedStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitThrowStatement(ThrowStatement node) {
		pushASTNode(node);
		try {
			super.visitThrowStatement(node);
		} finally {
			popASTNode();
		}
	}

	public void visitMethodCallExpression(MethodCallExpression node) {
		pushASTNode(node);
		try {
			super.visitMethodCallExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitStaticMethodCallExpression(StaticMethodCallExpression node) {
		pushASTNode(node);
		try {
			super.visitStaticMethodCallExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitConstructorCallExpression(ConstructorCallExpression node) {
		pushASTNode(node);
		try {
			super.visitConstructorCallExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitBinaryExpression(BinaryExpression node) {
		pushASTNode(node);
		try {
			captureMetaClassAssignment(node);
			super.visitBinaryExpression(node);
		} finally {
			popASTNode();
		}
	}

	private void captureMetaClassAssignment(BinaryExpression node) {
		String operation = node.getOperation() != null ? node.getOperation().getText() : null;
		if (!"=".equals(operation)) {
			return;
		}
		if (!(node.getLeftExpression() instanceof PropertyExpression)) {
			return;
		}
		PropertyExpression left = (PropertyExpression) node.getLeftExpression();
		String propertyName = left.getPropertyAsString();
		if (propertyName == null) {
			return;
		}
		if (!(left.getObjectExpression() instanceof PropertyExpression)) {
			return;
		}
		PropertyExpression metaClassExpr = (PropertyExpression) left.getObjectExpression();
		String metaClassName = metaClassExpr.getPropertyAsString();
		if (!"metaClass".equals(metaClassName)) {
			return;
		}
		Expression targetExpr = metaClassExpr.getObjectExpression();
		ClassNode targetType = null;
		if (targetExpr instanceof ClassExpression) {
			ClassNode exprType = ((ClassExpression) targetExpr).getType();
			if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type
					&& exprType.getGenericsTypes() != null && exprType.getGenericsTypes().length > 0) {
				targetType = exprType.getGenericsTypes()[0].getType();
			} else if (exprType != null && exprType.redirect() == ClassHelper.CLASS_Type) {
				String exprText = targetExpr.getText();
				targetType = resolveClassNodeByName(exprText);
			} else {
				targetType = exprType;
			}
		} else {
			ASTNode def = net.prominic.groovyls.compiler.util.GroovyASTUtils.getDefinition(targetExpr, false, this);
			if (def instanceof ClassNode) {
				targetType = (ClassNode) def;
			} else if (def instanceof VariableExpression) {
				ClassNode originType = ((VariableExpression) def).getOriginType();
				if (originType != null) {
					targetType = originType;
				}
			}
		}
		if (targetType == null) {
			String candidateName = null;
			if (targetExpr instanceof VariableExpression) {
				candidateName = ((VariableExpression) targetExpr).getName();
			} else if (targetExpr instanceof ConstantExpression) {
				candidateName = ((ConstantExpression) targetExpr).getText();
			}
			if (candidateName != null) {
				for (ClassNode classNode : getClassNodes()) {
					if (candidateName.equals(classNode.getName())
							|| candidateName.equals(classNode.getNameWithoutPackage())) {
						targetType = classNode;
						break;
					}
				}
			}
			if (targetType == null && candidateName != null) {
				targetType = new ClassNode(candidateName, 0, ClassHelper.OBJECT_TYPE);
			}
			if (targetType == null) {
				return;
			}
		}
		URI uri = sourceUnit != null ? sourceUnit.getSource().getURI() : null;
		if (node.getRightExpression() instanceof ClosureExpression) {
			MethodNode methodNode = new MethodNode(propertyName, 0, ClassHelper.dynamicType(), new Parameter[0],
					new ClassNode[0], null);
			methodNode.setDeclaringClass(targetType);
			if (targetType.getMethods(propertyName).isEmpty()) {
				targetType.addMethod(methodNode);
			}
			metaClassMethodsByType.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(propertyName,
					methodNode);
			String simpleName = targetType.getNameWithoutPackage();
			if (simpleName != null && !simpleName.equals(targetType.getName())) {
				metaClassMethodsByType.computeIfAbsent(simpleName, key -> new HashMap<>()).put(propertyName,
						methodNode);
			}
			if (uri != null) {
				metaClassMethodsByURI.computeIfAbsent(uri, key -> new HashMap<>())
						.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(propertyName, methodNode);
				if (simpleName != null && !simpleName.equals(targetType.getName())) {
					metaClassMethodsByURI.computeIfAbsent(uri, key -> new HashMap<>())
							.computeIfAbsent(simpleName, key -> new HashMap<>()).put(propertyName, methodNode);
				}
			}
		} else {
			PropertyNode propNode = new PropertyNode(propertyName, 0, ClassHelper.dynamicType(),
					targetType, null, null, null);
			propNode.setDeclaringClass(targetType);
			if (targetType.getProperty(propertyName) == null) {
				targetType.addProperty(propNode);
			}
			metaClassPropertiesByType.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(propertyName,
					propNode);
			String simpleName = targetType.getNameWithoutPackage();
			if (simpleName != null && !simpleName.equals(targetType.getName())) {
				metaClassPropertiesByType.computeIfAbsent(simpleName, key -> new HashMap<>()).put(propertyName,
						propNode);
			}
			if (uri != null) {
				metaClassPropertiesByURI.computeIfAbsent(uri, key -> new HashMap<>())
						.computeIfAbsent(targetType.getName(), key -> new HashMap<>()).put(propertyName, propNode);
				if (simpleName != null && !simpleName.equals(targetType.getName())) {
					metaClassPropertiesByURI.computeIfAbsent(uri, key -> new HashMap<>())
							.computeIfAbsent(simpleName, key -> new HashMap<>()).put(propertyName, propNode);
				}
			}
		}
	}

	private ClassNode resolveClassNodeByName(String className) {
		if (className == null || className.isBlank()) {
			return null;
		}
		String normalized = className.trim();
		if (normalized.endsWith(".class")) {
			normalized = normalized.substring(0, normalized.length() - ".class".length());
		}
		for (ClassNode classNode : getClassNodes()) {
			if (normalized.equals(classNode.getName())
					|| normalized.equals(classNode.getNameWithoutPackage())) {
				return classNode;
			}
		}
		return new ClassNode(normalized, 0, ClassHelper.OBJECT_TYPE);
	}

	public void visitTernaryExpression(TernaryExpression node) {
		pushASTNode(node);
		try {
			super.visitTernaryExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitShortTernaryExpression(ElvisOperatorExpression node) {
		pushASTNode(node);
		try {
			super.visitShortTernaryExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitPostfixExpression(PostfixExpression node) {
		pushASTNode(node);
		try {
			super.visitPostfixExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitPrefixExpression(PrefixExpression node) {
		pushASTNode(node);
		try {
			super.visitPrefixExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitBooleanExpression(BooleanExpression node) {
		pushASTNode(node);
		try {
			super.visitBooleanExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitNotExpression(NotExpression node) {
		pushASTNode(node);
		try {
			super.visitNotExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitClosureExpression(ClosureExpression node) {
		pushASTNode(node);
		try {
			super.visitClosureExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitTupleExpression(TupleExpression node) {
		pushASTNode(node);
		try {
			super.visitTupleExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitListExpression(ListExpression node) {
		pushASTNode(node);
		try {
			super.visitListExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitArrayExpression(ArrayExpression node) {
		pushASTNode(node);
		try {
			super.visitArrayExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitMapExpression(MapExpression node) {
		pushASTNode(node);
		try {
			super.visitMapExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitMapEntryExpression(MapEntryExpression node) {
		pushASTNode(node);
		try {
			super.visitMapEntryExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitRangeExpression(RangeExpression node) {
		pushASTNode(node);
		try {
			super.visitRangeExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitSpreadExpression(SpreadExpression node) {
		pushASTNode(node);
		try {
			super.visitSpreadExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitSpreadMapExpression(SpreadMapExpression node) {
		pushASTNode(node);
		try {
			super.visitSpreadMapExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitMethodPointerExpression(MethodPointerExpression node) {
		pushASTNode(node);
		try {
			super.visitMethodPointerExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitUnaryMinusExpression(UnaryMinusExpression node) {
		pushASTNode(node);
		try {
			super.visitUnaryMinusExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitUnaryPlusExpression(UnaryPlusExpression node) {
		pushASTNode(node);
		try {
			super.visitUnaryPlusExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitBitwiseNegationExpression(BitwiseNegationExpression node) {
		pushASTNode(node);
		try {
			super.visitBitwiseNegationExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitCastExpression(CastExpression node) {
		pushASTNode(node);
		try {
			super.visitCastExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitConstantExpression(ConstantExpression node) {
		pushASTNode(node);
		try {
			super.visitConstantExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitClassExpression(ClassExpression node) {
		pushASTNode(node);
		try {
			super.visitClassExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitVariableExpression(VariableExpression node) {
		pushASTNode(node);
		try {
			super.visitVariableExpression(node);
		} finally {
			popASTNode();
		}
	}

	// this calls visitBinaryExpression()
	// public void visitDeclarationExpression(DeclarationExpression node) {
	// pushASTNode(node);
	// try {
	// super.visitDeclarationExpression(node);
	// } finally {
	// popASTNode();
	// }
	// }

	public void visitPropertyExpression(PropertyExpression node) {
		pushASTNode(node);
		try {
			super.visitPropertyExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitAttributeExpression(AttributeExpression node) {
		pushASTNode(node);
		try {
			super.visitAttributeExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitFieldExpression(FieldExpression node) {
		pushASTNode(node);
		try {
			super.visitFieldExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitGStringExpression(GStringExpression node) {
		pushASTNode(node);
		try {
			super.visitGStringExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitCatchStatement(CatchStatement node) {
		pushASTNode(node);
		try {
			super.visitCatchStatement(node);
		} finally {
			popASTNode();
		}
	}

	// this calls visitTupleListExpression()
	// public void visitArgumentlistExpression(ArgumentListExpression node) {
	// pushASTNode(node);
	// try {
	// super.visitArgumentlistExpression(node);
	// } finally {
	// popASTNode();
	// }
	// }

	public void visitClosureListExpression(ClosureListExpression node) {
		pushASTNode(node);
		try {
			super.visitClosureListExpression(node);
		} finally {
			popASTNode();
		}
	}

	public void visitBytecodeExpression(BytecodeExpression node) {
		pushASTNode(node);
		try {
			super.visitBytecodeExpression(node);
		} finally {
			popASTNode();
		}
	}
}