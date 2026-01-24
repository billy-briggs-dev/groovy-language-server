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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.ScanResult;
import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.compiler.util.GroovydocUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;
import net.prominic.groovyls.util.FileContentsTracker;
import net.prominic.lsp.utils.Positions;

public class CompletionProvider {
	private static final Pattern METACLASS_METHOD_PATTERN = Pattern
			.compile("([A-Za-z_][\\w\\.]*)\\.metaClass\\.([A-Za-z_][\\w]*)\\s*=\\s*\\{");
	private static final List<String> KEYWORDS = Arrays.asList(
			"abstract", "as", "assert", "break", "case", "catch", "class", "continue", "def", "default",
			"do", "else", "enum", "extends", "false", "final", "for", "if", "implements", "import",
			"in", "instanceof", "interface", "native", "new", "null", "package", "private", "protected",
			"public", "return", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
			"throws", "trait", "transient", "true", "try", "volatile", "while");

	private ASTNodeVisitor ast;
	private ScanResult classGraphScanResult;
	private FileContentsTracker files;
	private URI completionUri;
	private Position completionPosition;
	private int maxItemCount = 5000;
	private boolean isIncomplete = false;

	public CompletionProvider(ASTNodeVisitor ast, ScanResult classGraphScanResult, FileContentsTracker files) {
		this.ast = ast;
		this.classGraphScanResult = classGraphScanResult;
		this.files = files;
	}

	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> provideCompletion(
			TextDocumentIdentifier textDocument, Position position, CompletionContext context) {
		if (ast == null) {
			// this shouldn't happen, but let's avoid an exception if something
			// goes terribly wrong.
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}
		URI uri = URI.create(textDocument.getUri());
		completionUri = uri;
		completionPosition = position;
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, position.getLine(), position.getCharacter());
		ASTNode parentNode = offsetNode != null ? ast.getParent(offsetNode) : null;

		isIncomplete = false;
		List<CompletionItem> items = new ArrayList<>();

		if (offsetNode instanceof PropertyExpression) {
			populateItemsFromPropertyExpression((PropertyExpression) offsetNode, position, items);
		} else if (parentNode instanceof PropertyExpression) {
			populateItemsFromPropertyExpression((PropertyExpression) parentNode, position, items);
		} else if (offsetNode instanceof MethodCallExpression) {
			populateItemsFromMethodCallExpression((MethodCallExpression) offsetNode, position, items);
		} else if (offsetNode instanceof ConstructorCallExpression) {
			populateItemsFromConstructorCallExpression((ConstructorCallExpression) offsetNode, position, items);
		} else if (parentNode instanceof MethodCallExpression) {
			populateItemsFromMethodCallExpression((MethodCallExpression) parentNode, position, items);
		} else if (offsetNode instanceof VariableExpression) {
			populateItemsFromVariableExpression((VariableExpression) offsetNode, position, items);
		} else if (offsetNode instanceof ImportNode) {
			populateItemsFromImportNode((ImportNode) offsetNode, position, items);
		} else if (offsetNode instanceof ClassNode) {
			populateItemsFromClassNode((ClassNode) offsetNode, position, items);
		} else if (offsetNode instanceof MethodNode) {
			populateItemsFromScope(offsetNode, "", items);
		} else if (offsetNode instanceof Statement) {
			populateItemsFromScope(offsetNode, "", items);
		}

		if (items.isEmpty()) {
			PropertyExpression fallback = findPropertyExpressionAtPosition(uri, position);
			if (fallback != null) {
				populateItemsFromPropertyExpression(fallback, position, items);
			}
		}

		if (items.isEmpty() && offsetNode instanceof MethodCallExpression && isCallResultMemberAccess(position)) {
			String prefix = getMemberNameFromSource(position);
			if (prefix == null) {
				prefix = "";
			}
			populateItemsFromExpression((MethodCallExpression) offsetNode, prefix, items);
		}

		boolean hasMemberItems = items.stream().anyMatch(item -> {
			CompletionItemKind kind = item != null ? item.getKind() : null;
			return CompletionItemKind.Method.equals(kind)
					|| CompletionItemKind.Field.equals(kind)
					|| CompletionItemKind.Property.equals(kind);
		});
		if (hasMemberAccessInSource(position) && !hasMemberItems) {
			String leftName = getLeftExpressionNameFromSource(position);
			if (leftName != null) {
				VariableExpression varExpr = findVariableExpressionAtPosition(uri, leftName, position);
				ClassNode inferredType = resolveTypeForNameAtPosition(uri, leftName, position);
				String prefix = getMemberNameFromSource(position);
				if (prefix == null) {
					prefix = "";
				}
				if (inferredType != null) {
					populateItemsFromType(inferredType, prefix, items);
				} else if (varExpr != null) {
					populateItemsFromExpression(varExpr, prefix, items);
				}
			}
		}

		if (items.isEmpty() && hasMemberAccessInSource(position)) {
			String prefix = getMemberNameFromSource(position);
			if (prefix == null) {
				prefix = "";
			}
			populateItemsFromSourceMetaClassAssignments(prefix, items);
		}

		items.forEach(item -> {
			if (item != null && item.getKind() == null) {
				item.setKind(CompletionItemKind.Property);
			}
		});

		if (isIncomplete) {
			return CompletableFuture.completedFuture(Either.forRight(new CompletionList(true, items)));
		}
		return CompletableFuture.completedFuture(Either.forLeft(items));
	}

	private PropertyExpression findPropertyExpressionAtPosition(URI uri, Position position) {
		if (ast == null || uri == null) {
			return null;
		}
		List<ASTNode> nodes = ast.getNodes(uri);
		if (nodes == null || nodes.isEmpty()) {
			nodes = ast.getNodes();
		}
		if (nodes == null || nodes.isEmpty()) {
			return null;
		}
		PropertyExpression best = null;
		int bestStart = -1;
		for (ASTNode node : nodes) {
			if (!(node instanceof PropertyExpression)) {
				continue;
			}
			PropertyExpression propExpr = (PropertyExpression) node;
			Range propRange = GroovyLanguageServerUtils.astNodeToRange(propExpr.getProperty());
			if (propRange == null) {
				continue;
			}
			if (position.getLine() != propRange.getStart().getLine()) {
				continue;
			}
			int startChar = propRange.getStart().getCharacter();
			if (position.getCharacter() < startChar) {
				continue;
			}
			if (startChar > bestStart) {
				bestStart = startChar;
				best = propExpr;
			}
		}
		return best;
	}

	private void populateItemsFromPropertyExpression(PropertyExpression propExpr, Position position,
			List<CompletionItem> items) {
		Range propertyRange = GroovyLanguageServerUtils.astNodeToRange(propExpr.getProperty());
		String memberName;
		if (propertyRange == null) {
			memberName = getMemberNameFromSource(position);
			if (memberName == null) {
				return;
			}
		} else {
			memberName = getMemberName(propExpr.getPropertyAsString(), propertyRange, position);
		}
		populateItemsFromExpression(propExpr.getObjectExpression(), memberName, items);
	}

	private void populateItemsFromMethodCallExpression(MethodCallExpression methodCallExpr, Position position,
			List<CompletionItem> items) {
		Range methodRange = GroovyLanguageServerUtils.astNodeToRange(methodCallExpr.getMethod());
		if (methodRange == null) {
			return;
		}
		String memberName = getMemberName(methodCallExpr.getMethodAsString(), methodRange, position);
		populateItemsFromExpression(methodCallExpr.getObjectExpression(), memberName, items);
	}

	private void populateItemsFromImportNode(ImportNode importNode, Position position, List<CompletionItem> items) {
		Range importRange = GroovyLanguageServerUtils.astNodeToRange(importNode);
		if (importRange == null) {
			return;
		}
		// skip the "import " at the beginning
		importRange.setStart(new Position(importRange.getEnd().getLine(),
				importRange.getEnd().getCharacter() - importNode.getType().getName().length()));
		String importText = getMemberName(importNode.getType().getName(), importRange, position);

		ModuleNode enclosingModule = (ModuleNode) GroovyASTUtils.getEnclosingNodeOfType(importNode, ModuleNode.class,
				ast);

		String enclosingPackageName = enclosingModule != null ? enclosingModule.getPackageName() : null;
		List<String> importNames = enclosingModule != null ? enclosingModule.getImports().stream()
				.map(otherImportNode -> otherImportNode.getClassName()).collect(Collectors.toList())
				: Collections.emptyList();

		List<CompletionItem> localClassItems = ast.getClassNodes().stream().filter(classNode -> {
			String packageName = classNode.getPackageName();
			if (packageName == null || packageName.length() == 0 || packageName.equals(enclosingPackageName)) {
				return false;
			}
			String className = classNode.getName();
			String classNameWithoutPackage = classNode.getNameWithoutPackage();
			if (!className.startsWith(importText) && !classNameWithoutPackage.startsWith(importText)) {
				return false;
			}
			if (importNames.contains(className)) {
				return false;
			}
			return true;
		}).map(classNode -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(classNode.getName());
			item.setTextEdit(Either.forLeft(new TextEdit(importRange, classNode.getName())));
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(classNode));
			if (classNode.getNameWithoutPackage().startsWith(importText)) {
				item.setSortText(classNode.getNameWithoutPackage());
			}
			String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(classNode.getGroovydoc());
			if (markdownDocs != null) {
				item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(localClassItems);

		if (classGraphScanResult == null) {
			return;
		}
		List<ClassInfo> classes = classGraphScanResult.getAllClasses();
		List<PackageInfo> packages = classGraphScanResult.getPackageInfo();

		List<CompletionItem> packageItems = packages.stream().filter(packageInfo -> {
			String packageName = packageInfo.getName();
			if (packageName.startsWith(importText)) {
				return true;
			}
			return false;
		}).map(packageInfo -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(packageInfo.getName());
			item.setTextEdit(Either.forLeft(new TextEdit(importRange, packageInfo.getName())));
			item.setKind(CompletionItemKind.Module);
			return item;
		}).collect(Collectors.toList());
		items.addAll(packageItems);

		List<CompletionItem> classItems = classes.stream().filter(classInfo -> {
			String packageName = classInfo.getPackageName();
			if (packageName == null || packageName.length() == 0 || packageName.equals(enclosingPackageName)) {
				return false;
			}
			String className = classInfo.getName();
			String classNameWithoutPackage = classInfo.getSimpleName();
			if (!className.startsWith(importText) && !classNameWithoutPackage.startsWith(importText)) {
				return false;
			}
			if (importNames.contains(className)) {
				return false;
			}
			return true;
		}).map(classInfo -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(classInfo.getName());
			item.setTextEdit(Either.forLeft(new TextEdit(importRange, classInfo.getName())));
			item.setKind(classInfoToCompletionItemKind(classInfo));
			if (classInfo.getSimpleName().startsWith(importText)) {
				item.setSortText(classInfo.getSimpleName());
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(classItems);
	}

	private void populateItemsFromClassNode(ClassNode classNode, Position position, List<CompletionItem> items) {
		ASTNode parentNode = ast.getParent(classNode);
		if (!(parentNode instanceof ClassNode)) {
			return;
		}
		ClassNode parentClassNode = (ClassNode) parentNode;
		Range classRange = GroovyLanguageServerUtils.astNodeToRange(classNode);
		if (classRange == null) {
			return;
		}
		String className = getMemberName(classNode.getUnresolvedName(), classRange, position);
		if (classNode.equals(parentClassNode.getUnresolvedSuperClass())) {
			populateTypes(classNode, className, new HashSet<>(), true, false, false, items);
		} else if (Arrays.asList(parentClassNode.getUnresolvedInterfaces()).contains(classNode)) {
			populateTypes(classNode, className, new HashSet<>(), false, true, false, items);
		}
	}

	private void populateItemsFromConstructorCallExpression(ConstructorCallExpression constructorCallExpr,
			Position position, List<CompletionItem> items) {
		Range typeRange = GroovyLanguageServerUtils.astNodeToRange(constructorCallExpr.getType());
		if (typeRange == null) {
			return;
		}
		String typeName = getMemberName(constructorCallExpr.getType().getNameWithoutPackage(), typeRange, position);
		populateTypes(constructorCallExpr, typeName, new HashSet<>(), true, false, false, items);
	}

	private void populateItemsFromVariableExpression(VariableExpression varExpr, Position position,
			List<CompletionItem> items) {
		Range varRange = GroovyLanguageServerUtils.astNodeToRange(varExpr);
		if (varRange == null) {
			return;
		}
		String memberName = getMemberName(varExpr.getName(), varRange, position);
		populateItemsFromScope(varExpr, memberName, items);
	}

	private void populateItemsFromPropertiesAndFields(List<PropertyNode> properties, List<FieldNode> fields,
			String memberNamePrefix, Set<String> existingNames, List<CompletionItem> items) {
		List<CompletionItem> propItems = properties.stream().filter(property -> {
			String name = property.getName();
			// sometimes, a property and a field will have the same name
			if (name.startsWith(memberNamePrefix) && !existingNames.contains(name)) {
				existingNames.add(name);
				return true;
			}
			return false;
		}).map(property -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(property.getName());
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(property));
			String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(property.getGroovydoc());
			if (markdownDocs != null) {
				item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(propItems);
		List<CompletionItem> fieldItems = fields.stream().filter(field -> {
			String name = field.getName();
			// sometimes, a property and a field will have the same name
			if (name.startsWith(memberNamePrefix) && !existingNames.contains(name)) {
				existingNames.add(name);
				return true;
			}
			return false;
		}).map(field -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(field.getName());
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(field));
			String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(field.getGroovydoc());
			if (markdownDocs != null) {
				item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(fieldItems);
	}

	private void populateItemsFromMethods(List<MethodNode> methods, String memberNamePrefix, Set<String> existingNames,
			List<CompletionItem> items) {
		List<CompletionItem> methodItems = methods.stream().filter(method -> {
			String methodName = method.getName();
			// overloads can cause duplicates
			if (methodName.startsWith(memberNamePrefix) && !existingNames.contains(methodName)) {
				existingNames.add(methodName);
				return true;
			}
			return false;
		}).map(method -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(method.getName());
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(method));
			String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(method.getGroovydoc());
			if (markdownDocs != null) {
				item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(methodItems);
	}

	private void populateItemsFromExpression(Expression leftSide, String memberNamePrefix, List<CompletionItem> items) {
		Set<String> existingNames = new HashSet<>();

		List<PropertyNode> properties = GroovyASTUtils.getPropertiesForLeftSideOfPropertyExpression(leftSide, ast);
		List<FieldNode> fields = GroovyASTUtils.getFieldsForLeftSideOfPropertyExpression(leftSide, ast);
		populateItemsFromPropertiesAndFields(properties, fields, memberNamePrefix, existingNames, items);

		List<MethodNode> methods = GroovyASTUtils.getMethodsForLeftSideOfPropertyExpression(leftSide, ast);
		populateItemsFromMethods(methods, memberNamePrefix, existingNames, items);

		ClassNode leftType = GroovyASTUtils.getTypeOfNode(leftSide, ast);
		if (leftType == null || ClassHelper.isDynamicTyped(leftType) || leftType == ClassHelper.OBJECT_TYPE) {
			ClassNode fallback = leftSide.getType();
			if (fallback != null && !ClassHelper.isDynamicTyped(fallback)) {
				leftType = fallback;
			}
		}
		if (leftType == null || ClassHelper.isDynamicTyped(leftType) || leftType == ClassHelper.OBJECT_TYPE) {
			ASTNode def = GroovyASTUtils.getDefinition(leftSide, false, ast);
			if (def instanceof org.codehaus.groovy.ast.Variable) {
				org.codehaus.groovy.ast.Variable variable = (org.codehaus.groovy.ast.Variable) def;
				ClassNode origin = variable.getOriginType();
				if (origin != null && !ClassHelper.isDynamicTyped(origin)) {
					leftType = origin;
				} else {
					ClassNode varType = variable.getType();
					if (varType != null && !ClassHelper.isDynamicTyped(varType)) {
						leftType = varType;
					}
				}
				if ((leftType == null || ClassHelper.isDynamicTyped(leftType)
						|| leftType == ClassHelper.OBJECT_TYPE) && def instanceof VariableExpression) {
					VariableExpression varExpr = (VariableExpression) def;
					if (varExpr.hasInitialExpression()) {
						leftType = GroovyASTUtils.getTypeOfNode(varExpr.getInitialExpression(), ast);
					}
				}
			}
			if ((leftType == null || ClassHelper.isDynamicTyped(leftType) || leftType == ClassHelper.OBJECT_TYPE)
					&& leftSide instanceof VariableExpression) {
				String varName = ((VariableExpression) leftSide).getName();
				for (ASTNode candidate : ast.getNodes()) {
					if (candidate instanceof DeclarationExpression) {
						DeclarationExpression decl = (DeclarationExpression) candidate;
						if (decl.getVariableExpression() != null
								&& varName.equals(decl.getVariableExpression().getName())) {
							leftType = GroovyASTUtils.getTypeOfNode(decl.getRightExpression(), ast);
							break;
						}
					}
				}
			}
		}
		if ((leftType == null || ClassHelper.isDynamicTyped(leftType) || leftType == ClassHelper.OBJECT_TYPE)
				&& leftSide instanceof VariableExpression) {
			Position inferredPosition = completionPosition;
			if (inferredPosition == null && leftSide.getLineNumber() > 0 && leftSide.getColumnNumber() > 0) {
				inferredPosition = new Position(leftSide.getLineNumber() - 1, leftSide.getColumnNumber() - 1);
			}
			String name = ((VariableExpression) leftSide).getName();
			ClassNode inferredType = resolveTypeForNameAtPosition(completionUri, name, inferredPosition);
			if (inferredType != null) {
				leftType = inferredType;
			}
		}
		if (leftType != null) {
			populateItemsFromPropertiesAndFields(ast.getMetaClassProperties(leftType),
					Collections.emptyList(), memberNamePrefix, existingNames, items);
			populateItemsFromMethods(ast.getMetaClassMethods(leftType), memberNamePrefix, existingNames, items);
		}
		populateItemsFromMetaClassAssignments(leftType, memberNamePrefix, existingNames, items);
		populateItemsFromSourceMetaClassAssignments(leftSide, leftType, memberNamePrefix, existingNames, items);
		populateItemsFromSourceMetaClassAssignments(memberNamePrefix, items);
	}

	private void populateItemsFromSourceMetaClassAssignments(Expression leftSide, ClassNode leftType,
			String memberNamePrefix, Set<String> existingNames, List<CompletionItem> items) {
		if (files == null || ast == null || leftSide == null) {
			return;
		}
		URI uri = ast.getURI(leftSide);
		if (uri == null) {
			uri = completionUri;
		}
		if (uri == null) {
			return;
		}
		String text = files.getContents(uri);
		if (text == null || text.isBlank()) {
			return;
		}
		boolean matchAllTypes = leftType == null || ClassHelper.isDynamicTyped(leftType)
				|| leftType == ClassHelper.OBJECT_TYPE
				|| "groovy.lang.GroovyObject".equals(leftType.getName());
		String targetName = matchAllTypes ? null : leftType.getName();
		String targetSimple = matchAllTypes ? null : leftType.getNameWithoutPackage();
		Matcher matcher = METACLASS_METHOD_PATTERN.matcher(text);
		while (matcher.find()) {
			String className = matcher.group(1);
			String methodName = matcher.group(2);
			if (!matchAllTypes) {
				boolean matches = className.equals(targetName)
						|| (targetSimple != null && className.equals(targetSimple));
				if (!matches) {
					continue;
				}
			}
			if (!methodName.startsWith(memberNamePrefix) || existingNames.contains(methodName)) {
				continue;
			}
			CompletionItem item = new CompletionItem();
			item.setLabel(methodName);
			item.setKind(CompletionItemKind.Method);
			items.add(item);
			existingNames.add(methodName);
		}
	}

	private void populateItemsFromMetaClassAssignments(ClassNode leftType, String memberNamePrefix,
			Set<String> existingNames, List<CompletionItem> items) {
		boolean matchAllTypes = leftType == null || ClassHelper.isDynamicTyped(leftType)
				|| leftType == ClassHelper.OBJECT_TYPE
				|| "groovy.lang.GroovyObject".equals(leftType.getName());
		String targetName = matchAllTypes ? null : leftType.getName();
		String targetSimple = matchAllTypes ? null : leftType.getNameWithoutPackage();
		for (ASTNode node : ast.getNodes()) {
			if (!(node instanceof BinaryExpression)) {
				continue;
			}
			BinaryExpression binary = (BinaryExpression) node;
			if (binary.getOperation() == null || !"=".equals(binary.getOperation().getText())) {
				continue;
			}
			if (!(binary.getLeftExpression() instanceof PropertyExpression)) {
				continue;
			}
			PropertyExpression left = (PropertyExpression) binary.getLeftExpression();
			String methodName = left.getPropertyAsString();
			if (methodName == null) {
				continue;
			}
			if (!(left.getObjectExpression() instanceof PropertyExpression)) {
				continue;
			}
			PropertyExpression metaClassExpr = (PropertyExpression) left.getObjectExpression();
			if (!"metaClass".equals(metaClassExpr.getPropertyAsString())) {
				continue;
			}
			String className = null;
			Expression targetExpr = metaClassExpr.getObjectExpression();
			if (targetExpr instanceof ClassExpression) {
				ClassNode exprType = ((ClassExpression) targetExpr).getType();
				if (exprType != null) {
					if (exprType.redirect() == ClassHelper.CLASS_Type) {
						String exprText = targetExpr.getText();
						if (exprText != null && exprText.endsWith(".class")) {
							exprText = exprText.substring(0, exprText.length() - ".class".length());
						}
						className = exprText;
					} else {
						className = exprType.getName();
					}
				}
			} else if (targetExpr instanceof VariableExpression) {
				className = ((VariableExpression) targetExpr).getName();
			}
			if (className == null && !matchAllTypes) {
				continue;
			}
			boolean matches = matchAllTypes || className.equals(targetName)
					|| (targetSimple != null && className.equals(targetSimple));
			if (!matches) {
				continue;
			}
			if (!(binary.getRightExpression() instanceof ClosureExpression)) {
				continue;
			}
			if (!methodName.startsWith(memberNamePrefix) || existingNames.contains(methodName)) {
				continue;
			}
			CompletionItem item = new CompletionItem();
			item.setLabel(methodName);
			item.setKind(CompletionItemKind.Method);
			items.add(item);
			existingNames.add(methodName);
		}
	}

	private void populateItemsFromVariableScope(VariableScope variableScope, String memberNamePrefix,
			Set<String> existingNames, List<CompletionItem> items) {
		List<CompletionItem> variableItems = variableScope.getDeclaredVariables().values().stream().filter(variable -> {

			String variableName = variable.getName();
			// overloads can cause duplicates
			if (variableName.startsWith(memberNamePrefix) && !existingNames.contains(variableName)) {
				existingNames.add(variableName);
				return true;
			}
			return false;
		}).map(variable -> {
			CompletionItem item = new CompletionItem();
			item.setLabel(variable.getName());
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind((ASTNode) variable));
			if (variable instanceof AnnotatedNode) {
				AnnotatedNode annotatedVar = (AnnotatedNode) variable;
				String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(annotatedVar.getGroovydoc());
				if (markdownDocs != null) {
					item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
				}
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(variableItems);
	}

	private void populateItemsFromScope(ASTNode node, String namePrefix, List<CompletionItem> items) {
		Set<String> existingNames = new HashSet<>();
		populateDslContextItems(node, namePrefix, existingNames, items);
		ASTNode current = node;
		while (current != null) {
			if (current instanceof ClassNode) {
				ClassNode classNode = (ClassNode) current;
				populateItemsFromPropertiesAndFields(classNode.getProperties(), classNode.getFields(), namePrefix,
						existingNames, items);
				populateItemsFromMethods(classNode.getMethods(), namePrefix, existingNames, items);
			} else if (current instanceof MethodNode) {
				MethodNode methodNode = (MethodNode) current;
				populateItemsFromVariableScope(methodNode.getVariableScope(), namePrefix, existingNames, items);
			} else if (current instanceof BlockStatement) {
				BlockStatement block = (BlockStatement) current;
				populateItemsFromVariableScope(block.getVariableScope(), namePrefix, existingNames, items);
			}
			current = ast.getParent(current);
		}
		populateKeywordItems(namePrefix, existingNames, items);
		if (namePrefix != null && !namePrefix.isEmpty()) {
			populateTypes(node, namePrefix, existingNames, items);
		}
	}

	private void populateDslContextItems(ASTNode node, String namePrefix, Set<String> existingNames,
			List<CompletionItem> items) {
		ClassNode delegateType = GroovyASTUtils.getDelegatesToType(node, ast);
		if (delegateType == null) {
			return;
		}
		String prefix = namePrefix == null ? "" : namePrefix;
		List<PropertyNode> properties = delegateType.getProperties().stream().filter(prop -> !prop.isStatic())
				.collect(Collectors.toList());
		List<FieldNode> fields = delegateType.getFields().stream().filter(field -> !field.isStatic())
				.collect(Collectors.toList());
		populateItemsFromPropertiesAndFields(properties, fields, prefix, existingNames, items);
		List<MethodNode> methods = delegateType.getMethods().stream().filter(method -> !method.isStatic())
				.collect(Collectors.toList());
		populateItemsFromMethods(methods, prefix, existingNames, items);
		populateItemsFromPropertiesAndFields(ast.getMetaClassProperties(delegateType), Collections.emptyList(),
				prefix, existingNames, items);
		populateItemsFromMethods(ast.getMetaClassMethods(delegateType), prefix, existingNames, items);
	}

	private void populateKeywordItems(String namePrefix, Set<String> existingNames, List<CompletionItem> items) {
		String prefix = namePrefix == null ? "" : namePrefix;
		for (String keyword : KEYWORDS) {
			if (!keyword.startsWith(prefix) || existingNames.contains(keyword)) {
				continue;
			}
			CompletionItem item = new CompletionItem();
			item.setLabel(keyword);
			item.setKind(CompletionItemKind.Keyword);
			items.add(item);
			existingNames.add(keyword);
		}
	}

	private void populateTypes(ASTNode offsetNode, String namePrefix, Set<String> existingNames,
			List<CompletionItem> items) {
		populateTypes(offsetNode, namePrefix, existingNames, true, true, true, items);
	}

	private void populateTypes(ASTNode offsetNode, String namePrefix, Set<String> existingNames, boolean includeClasses,
			boolean includeInterfaces, boolean includeEnums, List<CompletionItem> items) {
		Range addImportRange = GroovyASTUtils.findAddImportRange(offsetNode, ast);

		ModuleNode enclosingModule = (ModuleNode) GroovyASTUtils.getEnclosingNodeOfType(offsetNode, ModuleNode.class,
				ast);
		String enclosingPackageName = enclosingModule != null ? enclosingModule.getPackageName() : null;
		List<String> importNames = enclosingModule != null
				? enclosingModule.getImports().stream().map(importNode -> importNode.getClassName())
						.collect(Collectors.toList())
				: Collections.emptyList();

		List<CompletionItem> localClassItems = ast.getClassNodes().stream().filter(classNode -> {
			if (isIncomplete) {
				return false;
			}
			if (existingNames.size() >= maxItemCount) {
				isIncomplete = true;
				return false;
			}
			String classNameWithoutPackage = classNode.getNameWithoutPackage();
			String className = classNode.getName();
			if (classNameWithoutPackage.startsWith(namePrefix) && !existingNames.contains(className)) {
				existingNames.add(className);
				return true;
			}
			return false;
		}).map(classNode -> {
			String className = classNode.getName();
			String packageName = classNode.getPackageName();
			CompletionItem item = new CompletionItem();
			item.setLabel(classNode.getNameWithoutPackage());
			item.setKind(GroovyLanguageServerUtils.astNodeToCompletionItemKind(classNode));
			item.setDetail(packageName);
			String markdownDocs = GroovydocUtils.groovydocToMarkdownDescription(classNode.getGroovydoc());
			if (markdownDocs != null) {
				item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdownDocs));
			}
			if (packageName != null && !packageName.equals(enclosingPackageName) && !importNames.contains(className)) {
				List<TextEdit> additionalTextEdits = new ArrayList<>();
				TextEdit addImportEdit = createAddImportTextEdit(className, addImportRange);
				additionalTextEdits.add(addImportEdit);
				item.setAdditionalTextEdits(additionalTextEdits);
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(localClassItems);

		if (classGraphScanResult == null) {
			return;
		}
		List<ClassInfo> classes = classGraphScanResult.getAllClasses();

		List<CompletionItem> classItems = classes.stream().filter(classInfo -> {
			if (isIncomplete) {
				return false;
			}
			if (existingNames.size() >= maxItemCount) {
				isIncomplete = true;
				return false;
			}
			String className = classInfo.getName();
			String classNameWithoutPackage = classInfo.getSimpleName();
			if (classNameWithoutPackage.startsWith(namePrefix) && !existingNames.contains(className)) {
				existingNames.add(className);
				return true;
			}
			return false;
		}).map(classInfo -> {
			String className = classInfo.getName();
			String packageName = classInfo.getPackageName();
			CompletionItem item = new CompletionItem();
			item.setLabel(classInfo.getSimpleName());
			item.setDetail(packageName);
			item.setKind(classInfoToCompletionItemKind(classInfo));
			if (packageName != null && !packageName.equals(enclosingPackageName) && !importNames.contains(className)) {
				List<TextEdit> additionalTextEdits = new ArrayList<>();
				TextEdit addImportEdit = createAddImportTextEdit(className, addImportRange);
				additionalTextEdits.add(addImportEdit);
				item.setAdditionalTextEdits(additionalTextEdits);
			}
			return item;
		}).collect(Collectors.toList());
		items.addAll(classItems);
	}

	private String getMemberName(String memberName, Range range, Position position) {
		if (position.getLine() == range.getStart().getLine()
				&& position.getCharacter() > range.getStart().getCharacter()) {
			int length = position.getCharacter() - range.getStart().getCharacter();
			if (length > 0 && length <= memberName.length()) {
				return memberName.substring(0, length).trim();
			}
		}
		return "";
	}

	private String getMemberNameFromSource(Position position) {
		if (files == null || completionUri == null) {
			return null;
		}
		String text = files.getContents(completionUri);
		if (text == null) {
			return null;
		}
		int offset = Positions.getOffset(text, position);
		int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
		lineStart = lineStart == -1 ? 0 : lineStart + 1;
		int lineEnd = text.indexOf('\n', offset);
		if (lineEnd == -1) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);
		int column = position.getCharacter();
		if (column > line.length()) {
			column = line.length();
		}
		int lastDot = line.lastIndexOf('.', Math.max(0, column - 1));
		if (lastDot == -1) {
			return "";
		}
		return line.substring(lastDot + 1, column).trim();
	}

	private boolean hasMemberAccessInSource(Position position) {
		if (files == null || completionUri == null) {
			return false;
		}
		String text = files.getContents(completionUri);
		if (text == null) {
			return false;
		}
		int offset = Positions.getOffset(text, position);
		int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
		lineStart = lineStart == -1 ? 0 : lineStart + 1;
		int lineEnd = text.indexOf('\n', offset);
		if (lineEnd == -1) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);
		int column = position.getCharacter();
		if (column > line.length()) {
			column = line.length();
		}
		int lastDot = line.lastIndexOf('.', Math.max(0, column - 1));
		return lastDot != -1;
	}

	private String getLeftExpressionNameFromSource(Position position) {
		if (files == null || completionUri == null) {
			return null;
		}
		String text = files.getContents(completionUri);
		if (text == null) {
			return null;
		}
		int offset = Positions.getOffset(text, position);
		int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
		lineStart = lineStart == -1 ? 0 : lineStart + 1;
		int lineEnd = text.indexOf('\n', offset);
		if (lineEnd == -1) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);
		int column = position.getCharacter();
		if (column > line.length()) {
			column = line.length();
		}
		int lastDot = line.lastIndexOf('.', Math.max(0, column - 1));
		if (lastDot == -1) {
			return null;
		}
		String left = line.substring(0, lastDot).trim();
		if (left.endsWith(")")) {
			return null;
		}
		int i = left.length() - 1;
		while (i >= 0 && Character.isJavaIdentifierPart(left.charAt(i))) {
			i--;
		}
		String name = left.substring(i + 1).trim();
		return name.isEmpty() ? null : name;
	}

	private VariableExpression findVariableExpressionAtPosition(URI uri, String name, Position position) {
		if (uri == null || name == null || ast == null) {
			return null;
		}
		List<ASTNode> nodes = ast.getNodes(uri);
		if (nodes == null || nodes.isEmpty()) {
			return null;
		}
		VariableExpression best = null;
		Position bestStart = null;
		for (ASTNode node : nodes) {
			if (!(node instanceof VariableExpression)) {
				continue;
			}
			VariableExpression varExpr = (VariableExpression) node;
			if (!name.equals(varExpr.getName())) {
				continue;
			}
			Range range = GroovyLanguageServerUtils.astNodeToRange(varExpr);
			if (range == null) {
				continue;
			}
			Position start = range.getStart();
			if (start.getLine() > position.getLine()
					|| (start.getLine() == position.getLine() && start.getCharacter() > position.getCharacter())) {
				continue;
			}
			if (bestStart == null || start.getLine() > bestStart.getLine()
					|| (start.getLine() == bestStart.getLine()
							&& start.getCharacter() > bestStart.getCharacter())) {
				bestStart = start;
				best = varExpr;
			}
		}
		return best;
	}

	private ClassNode resolveTypeForNameAtPosition(URI uri, String name, Position position) {
		if (uri == null || name == null || ast == null) {
			return null;
		}
		List<ASTNode> nodes = ast.getNodes(uri);
		if (nodes == null || nodes.isEmpty()) {
			return null;
		}
		ClassNode bestType = null;
		Position bestStart = null;
		for (ASTNode node : nodes) {
			Range range = null;
			ClassNode candidateType = null;
			if (node instanceof DeclarationExpression) {
				DeclarationExpression decl = (DeclarationExpression) node;
				if (decl.getVariableExpression() != null
						&& name.equals(decl.getVariableExpression().getName())) {
					range = GroovyLanguageServerUtils.astNodeToRange(decl.getVariableExpression());
					candidateType = GroovyASTUtils.getTypeOfNode(decl.getRightExpression(), ast);
				}
			} else if (node instanceof BinaryExpression) {
				BinaryExpression binary = (BinaryExpression) node;
				if (binary.getLeftExpression() instanceof VariableExpression) {
					VariableExpression varExpr = (VariableExpression) binary.getLeftExpression();
					if (name.equals(varExpr.getName()) && isAssignmentOperator(binary.getOperation() != null
							? binary.getOperation().getText()
							: null)) {
						range = GroovyLanguageServerUtils.astNodeToRange(varExpr);
						candidateType = GroovyASTUtils.getTypeOfNode(binary.getRightExpression(), ast);
					}
				}
			}
			if (candidateType == null) {
				continue;
			}
			Position start = range != null ? range.getStart() : null;
			if (start != null) {
				if (start.getLine() > position.getLine()
						|| (start.getLine() == position.getLine()
								&& start.getCharacter() > position.getCharacter())) {
					continue;
				}
			}
			if (bestStart == null || start == null || start.getLine() > bestStart.getLine()
					|| (start.getLine() == bestStart.getLine()
							&& start.getCharacter() > bestStart.getCharacter())) {
				bestStart = start;
				bestType = candidateType;
			}
		}
		if (bestType != null) {
			return bestType;
		}
		return resolveTypeFromSourceText(uri, name, position);
	}

	private ClassNode resolveTypeFromSourceText(URI uri, String name, Position position) {
		if (files == null || uri == null || name == null) {
			return null;
		}
		String text = files.getContents(uri);
		if (text == null) {
			return null;
		}
		String[] lines = text.split("\\r?\\n");
		int maxLine = Math.min(position.getLine(), lines.length - 1);
		Pattern pattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b\\s*=\\s*(.+)");
		String rhs = null;
		for (int i = 0; i <= maxLine; i++) {
			String line = lines[i];
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				rhs = matcher.group(1).trim();
			}
		}
		if (rhs == null || rhs.isEmpty()) {
			return null;
		}
		if (rhs.startsWith("'") || rhs.startsWith("\"")) {
			return ClassHelper.STRING_TYPE;
		}
		if (rhs.startsWith("[")) {
			return rhs.contains(":") ? ClassHelper.MAP_TYPE : ClassHelper.LIST_TYPE;
		}
		if (rhs.startsWith("true") || rhs.startsWith("false")) {
			return ClassHelper.Boolean_TYPE;
		}
		return null;
	}

	private void populateItemsFromType(ClassNode leftType, String memberNamePrefix, List<CompletionItem> items) {
		if (leftType == null) {
			return;
		}
		Set<String> existingNames = new HashSet<>();
		List<ClassNode> classNodes = new ArrayList<>();
		classNodes.add(leftType);
		int i = 0;
		while (i < classNodes.size()) {
			ClassNode current = classNodes.get(i);
			List<PropertyNode> properties = current.getProperties().stream().filter(prop -> !prop.isStatic())
					.collect(Collectors.toList());
			List<FieldNode> fields = current.getFields().stream().filter(field -> !field.isStatic())
					.collect(Collectors.toList());
			populateItemsFromPropertiesAndFields(properties, fields, memberNamePrefix, existingNames, items);
			List<MethodNode> methods = current.getMethods().stream().filter(method -> !method.isStatic())
					.collect(Collectors.toList());
			populateItemsFromMethods(methods, memberNamePrefix, existingNames, items);
			populateItemsFromPropertiesAndFields(ast.getMetaClassProperties(current), Collections.emptyList(),
					memberNamePrefix, existingNames, items);
			populateItemsFromMethods(ast.getMetaClassMethods(current), memberNamePrefix, existingNames, items);
			if (current.isInterface()) {
				for (ClassNode interfaceNode : current.getInterfaces()) {
					classNodes.add(interfaceNode);
				}
			} else {
				ClassNode superClassNode = null;
				try {
					superClassNode = current.getSuperClass();
				} catch (NoClassDefFoundError e) {
					// ignore missing classpath
				}
				if (superClassNode != null) {
					classNodes.add(superClassNode);
				}
			}
			i++;
		}
		populateItemsFromMetaClassAssignments(leftType, memberNamePrefix, existingNames, items);
	}

	private boolean isAssignmentOperator(String opText) {
		if (opText == null) {
			return false;
		}
		String op = opText.trim();
		if (op.equals("==") || op.equals("!=")) {
			return false;
		}
		return op.equals("=") || op.endsWith("=");
	}

	private boolean isCallResultMemberAccess(Position position) {
		if (files == null || completionUri == null) {
			return false;
		}
		String text = files.getContents(completionUri);
		if (text == null) {
			return false;
		}
		int offset = Positions.getOffset(text, position);
		int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
		lineStart = lineStart == -1 ? 0 : lineStart + 1;
		int lineEnd = text.indexOf('\n', offset);
		if (lineEnd == -1) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);
		int column = position.getCharacter();
		if (column > line.length()) {
			column = line.length();
		}
		int lastDot = line.lastIndexOf('.', Math.max(0, column - 1));
		if (lastDot == -1) {
			return false;
		}
		int i = lastDot - 1;
		while (i >= 0 && Character.isWhitespace(line.charAt(i))) {
			i--;
		}
		return i >= 0 && line.charAt(i) == ')';
	}

	private void populateItemsFromSourceMetaClassAssignments(String memberNamePrefix, List<CompletionItem> items) {
		if (files == null || completionUri == null) {
			return;
		}
		String text = files.getContents(completionUri);
		if (text == null || text.isBlank()) {
			return;
		}
		Matcher matcher = METACLASS_METHOD_PATTERN.matcher(text);
		while (matcher.find()) {
			String methodName = matcher.group(2);
			boolean hasMethod = items.stream().anyMatch(item -> methodName.equals(item.getLabel())
					&& CompletionItemKind.Method.equals(item.getKind()));
			if (!methodName.startsWith(memberNamePrefix) || hasMethod) {
				continue;
			}
			CompletionItem item = new CompletionItem();
			item.setLabel(methodName);
			item.setKind(CompletionItemKind.Method);
			items.add(item);
		}
	}

	private CompletionItemKind classInfoToCompletionItemKind(ClassInfo classInfo) {
		if (classInfo.isInterface()) {
			return CompletionItemKind.Interface;
		}
		if (classInfo.isEnum()) {
			return CompletionItemKind.Enum;
		}
		return CompletionItemKind.Class;
	}

	private TextEdit createAddImportTextEdit(String className, Range range) {
		TextEdit edit = new TextEdit();
		StringBuilder builder = new StringBuilder();
		builder.append("import ");
		builder.append(className);
		builder.append("\n");
		edit.setNewText(builder.toString());
		edit.setRange(range);
		return edit;
	}
}
