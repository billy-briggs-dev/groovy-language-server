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
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.util.GroovyLanguageServerUtils;

public class CallHierarchyProvider {
	private ASTNodeVisitor ast;

	public CallHierarchyProvider(ASTNodeVisitor ast) {
		this.ast = ast;
	}

	public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
		if (ast == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		URI uri = URI.create(params.getTextDocument().getUri());
		ASTNode offsetNode = ast.getNodeAtLineAndColumn(uri, params.getPosition().getLine(), 
				params.getPosition().getCharacter());
		
		if (offsetNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		// Get the definition - should be a MethodNode
		ASTNode definitionNode = GroovyASTUtils.getDefinition(offsetNode, true, ast);
		
		if (!(definitionNode instanceof MethodNode)) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		MethodNode methodNode = (MethodNode) definitionNode;
		CallHierarchyItem item = createCallHierarchyItem(methodNode);
		
		if (item == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		return CompletableFuture.completedFuture(Collections.singletonList(item));
	}

	public CompletableFuture<List<CallHierarchyIncomingCall>> incomingCalls(CallHierarchyIncomingCallsParams params) {
		if (ast == null || params.getItem() == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		CallHierarchyItem item = params.getItem();
		MethodNode methodNode = findMethodNodeByNameAndUri(item.getName(), item.getUri());
		
		if (methodNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		List<CallHierarchyIncomingCall> incomingCalls = new ArrayList<>();
		
		// Find all method call expressions that call this method
		for (ASTNode node : ast.getNodes()) {
			if (node instanceof MethodCallExpression) {
				MethodCallExpression callExpr = (MethodCallExpression) node;
				MethodNode calledMethod = GroovyASTUtils.getMethodFromCallExpression(callExpr, ast);
				
				if (calledMethod != null && isSameMethod(calledMethod, methodNode)) {
					// Find the enclosing method of this call
					URI uri = ast.getURI(callExpr);
					if (uri != null) {
						MethodNode callerMethod = findEnclosingMethod(callExpr, uri);
						if (callerMethod != null) {
							CallHierarchyItem callerItem = createCallHierarchyItem(callerMethod);
							if (callerItem != null) {
								Range fromRange = GroovyLanguageServerUtils.astNodeToRange(callExpr);
								List<Range> fromRanges = fromRange != null ? 
										Collections.singletonList(fromRange) : Collections.emptyList();
								
								CallHierarchyIncomingCall incomingCall = new CallHierarchyIncomingCall();
								incomingCall.setFrom(callerItem);
								incomingCall.setFromRanges(fromRanges);
								incomingCalls.add(incomingCall);
							}
						}
					}
				}
			}
		}
		
		return CompletableFuture.completedFuture(incomingCalls);
	}

	public CompletableFuture<List<CallHierarchyOutgoingCall>> outgoingCalls(CallHierarchyOutgoingCallsParams params) {
		if (ast == null || params.getItem() == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		
		CallHierarchyItem item = params.getItem();
		MethodNode methodNode = findMethodNodeByNameAndUri(item.getName(), item.getUri());
		
		if (methodNode == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		List<CallHierarchyOutgoingCall> outgoingCalls = new ArrayList<>();
		URI uri = URI.create(item.getUri());
		
		// Find all method calls within this method
		for (ASTNode node : ast.getNodes(uri)) {
			if (node instanceof MethodCallExpression) {
				MethodCallExpression callExpr = (MethodCallExpression) node;
				
				// Check if this call is within our method
				if (isNodeWithinMethod(callExpr, methodNode)) {
					MethodNode calledMethod = GroovyASTUtils.getMethodFromCallExpression(callExpr, ast);
					
					if (calledMethod != null) {
						CallHierarchyItem calledItem = createCallHierarchyItem(calledMethod);
						if (calledItem != null) {
							Range fromRange = GroovyLanguageServerUtils.astNodeToRange(callExpr);
							List<Range> fromRanges = fromRange != null ? 
									Collections.singletonList(fromRange) : Collections.emptyList();
							
							CallHierarchyOutgoingCall outgoingCall = new CallHierarchyOutgoingCall();
							outgoingCall.setTo(calledItem);
							outgoingCall.setFromRanges(fromRanges);
							outgoingCalls.add(outgoingCall);
						}
					}
				}
			}
		}
		
		return CompletableFuture.completedFuture(outgoingCalls);
	}

	private CallHierarchyItem createCallHierarchyItem(MethodNode methodNode) {
		if (methodNode == null || methodNode.getLineNumber() == -1) {
			return null;
		}

		URI uri = ast.getURI(methodNode);
		if (uri == null) {
			return null;
		}

		Range range = GroovyLanguageServerUtils.astNodeToRange(methodNode);
		Range selectionRange = range;

		CallHierarchyItem item = new CallHierarchyItem();
		item.setName(methodNode.getName());
		item.setKind(SymbolKind.Method);
		item.setUri(uri.toString());
		item.setRange(range);
		item.setSelectionRange(selectionRange);
		
		// Add class name as detail
		if (methodNode.getDeclaringClass() != null) {
			item.setDetail(methodNode.getDeclaringClass().getName());
		}

		return item;
	}

	private MethodNode findMethodNodeByNameAndUri(String methodName, String uriString) {
		if (methodName == null || uriString == null) {
			return null;
		}

		URI uri = URI.create(uriString);
		for (ASTNode node : ast.getNodes(uri)) {
			if (node instanceof MethodNode) {
				MethodNode methodNode = (MethodNode) node;
				if (methodName.equals(methodNode.getName())) {
					return methodNode;
				}
			}
		}
		
		return null;
	}

	private MethodNode findEnclosingMethod(ASTNode node, URI uri) {
		if (node == null || uri == null) {
			return null;
		}

		// Find the method that contains this node
		for (ASTNode candidate : ast.getNodes(uri)) {
			if (candidate instanceof MethodNode) {
				MethodNode methodNode = (MethodNode) candidate;
				if (isNodeWithinMethod(node, methodNode)) {
					return methodNode;
				}
			}
		}
		
		return null;
	}

	private boolean isNodeWithinMethod(ASTNode node, MethodNode method) {
		if (node == null || method == null) {
			return false;
		}

		int nodeStartLine = node.getLineNumber();
		int nodeEndLine = node.getLastLineNumber();
		int methodStartLine = method.getLineNumber();
		int methodEndLine = method.getLastLineNumber();

		return nodeStartLine >= methodStartLine && nodeEndLine <= methodEndLine;
	}

	private boolean isSameMethod(MethodNode method1, MethodNode method2) {
		if (method1 == method2) {
			return true;
		}
		
		if (method1 == null || method2 == null) {
			return false;
		}

		// Compare method names
		if (!method1.getName().equals(method2.getName())) {
			return false;
		}

		// Compare declaring classes
		if (method1.getDeclaringClass() != null && method2.getDeclaringClass() != null) {
			if (!method1.getDeclaringClass().getName().equals(method2.getDeclaringClass().getName())) {
				return false;
			}
		}

		// Compare parameter counts (simple check)
		int params1 = method1.getParameters() != null ? method1.getParameters().length : 0;
		int params2 = method2.getParameters() != null ? method2.getParameters().length : 0;
		if (params1 != params2) {
			return false;
		}

		return true;
	}
}
