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
package net.prominic.groovyls.providers.testing;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestDetectionProvider {
    
    private ASTNodeVisitor ast;

    public TestDetectionProvider(ASTNodeVisitor ast) {
        this.ast = ast;
    }

    /**
     * Provides code lenses for test classes and methods in the document.
     */
    public List<CodeLens> provideCodeLenses(URI uri) {
        if (ast == null) {
            return Collections.emptyList();
        }

        List<CodeLens> lenses = new ArrayList<>();
        List<ASTNode> nodes = ast.getNodes(uri);
        
        for (ASTNode node : nodes) {
            if (node instanceof ClassNode) {
                ClassNode classNode = (ClassNode) node;
                TestFramework framework = TestFrameworkDetector.detectFramework(classNode);
                
                if (framework != TestFramework.UNKNOWN) {
                    // Add code lens for test class
                    lenses.addAll(createTestClassLenses(classNode, framework));
                    
                    // Add code lenses for test methods
                    lenses.addAll(createTestMethodLenses(classNode, framework));
                }
            }
        }

        return lenses;
    }

    /**
     * Creates code lenses for a test class.
     */
    private List<CodeLens> createTestClassLenses(ClassNode classNode, TestFramework framework) {
        List<CodeLens> lenses = new ArrayList<>();
        
        int line = classNode.getLineNumber() - 1; // LSP is 0-based
        int column = classNode.getColumnNumber() - 1;
        
        Range range = new Range(
            new Position(line, column),
            new Position(line, column + classNode.getName().length())
        );
        
        // Create "Run All Tests" command for the class
        Command runCommand = new Command(
            "▶ Run All Tests",
            "groovyls.test.runClass",
            Collections.singletonList(classNode.getName())
        );
        
        CodeLens lens = new CodeLens(range, runCommand, null);
        lenses.add(lens);
        
        return lenses;
    }

    /**
     * Creates code lenses for test methods in a class.
     */
    private List<CodeLens> createTestMethodLenses(ClassNode classNode, TestFramework framework) {
        List<CodeLens> lenses = new ArrayList<>();
        
        List<MethodNode> methods = classNode.getMethods();
        for (MethodNode method : methods) {
            boolean isTest = false;
            
            if (framework == TestFramework.SPOCK) {
                isTest = TestFrameworkDetector.isSpockTestMethod(method, classNode);
            } else {
                isTest = TestFrameworkDetector.isTestMethod(method);
            }
            
            if (isTest) {
                int line = method.getLineNumber() - 1; // LSP is 0-based
                int column = method.getColumnNumber() - 1;
                
                Range range = new Range(
                    new Position(line, column),
                    new Position(line, column + method.getName().length())
                );
                
                // Create "Run Test" command for the method
                Command runCommand = new Command(
                    "▶ Run Test",
                    "groovyls.test.runMethod",
                    List.of(classNode.getName(), method.getName())
                );
                
                CodeLens lens = new CodeLens(range, runCommand, null);
                lenses.add(lens);
            }
        }
        
        return lenses;
    }

    /**
     * Gets all test information from a document.
     */
    public List<TestInfo> getTestInfo(URI uri) {
        if (ast == null) {
            return Collections.emptyList();
        }

        List<TestInfo> testInfoList = new ArrayList<>();
        List<ASTNode> nodes = ast.getNodes(uri);
        
        for (ASTNode node : nodes) {
            if (node instanceof ClassNode) {
                ClassNode classNode = (ClassNode) node;
                TestFramework framework = TestFrameworkDetector.detectFramework(classNode);
                
                if (framework != TestFramework.UNKNOWN) {
                    // Add test class info
                    int line = classNode.getLineNumber() - 1;
                    int column = classNode.getColumnNumber() - 1;
                    Range range = new Range(new Position(line, column), new Position(line, column));
                    
                    TestInfo classInfo = new TestInfo(
                        classNode.getName(),
                        framework,
                        range,
                        classNode.getName()
                    );
                    classInfo.setTestClass(true);
                    testInfoList.add(classInfo);
                    
                    // Add test method info
                    List<MethodNode> methods = classNode.getMethods();
                    for (MethodNode method : methods) {
                        boolean isTest = false;
                        
                        if (framework == TestFramework.SPOCK) {
                            isTest = TestFrameworkDetector.isSpockTestMethod(method, classNode);
                        } else {
                            isTest = TestFrameworkDetector.isTestMethod(method);
                        }
                        
                        if (isTest) {
                            int methodLine = method.getLineNumber() - 1;
                            int methodColumn = method.getColumnNumber() - 1;
                            Range methodRange = new Range(
                                new Position(methodLine, methodColumn),
                                new Position(methodLine, methodColumn)
                            );
                            
                            TestInfo methodInfo = new TestInfo(
                                method.getName(),
                                framework,
                                methodRange,
                                classNode.getName()
                            );
                            methodInfo.setTestMethod(true);
                            testInfoList.add(methodInfo);
                        }
                    }
                }
            }
        }

        return testInfoList;
    }
}
