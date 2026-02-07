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

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.List;

public class TestFrameworkDetector {
    
    private static final String JUNIT_TEST_ANNOTATION = "org.junit.jupiter.api.Test";
    private static final String JUNIT_TEST_ANNOTATION_SHORT = "Test";
    private static final String TESTNG_TEST_ANNOTATION = "org.testng.annotations.Test";
    private static final String SPOCK_SPECIFICATION = "spock.lang.Specification";

    /**
     * Detects if a class is a Spock test specification.
     * A Spock test extends spock.lang.Specification.
     */
    public static boolean isSpockTest(ClassNode classNode) {
        if (classNode == null) {
            return false;
        }
        
        // Check if the class extends Specification
        ClassNode superClass = classNode.getSuperClass();
        while (superClass != null && !superClass.getName().equals("java.lang.Object")) {
            if (SPOCK_SPECIFICATION.equals(superClass.getName())) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        
        return false;
    }

    /**
     * Detects if a method is a test method based on annotations.
     * Checks for JUnit @Test or TestNG @Test annotations.
     */
    public static boolean isTestMethod(MethodNode methodNode) {
        if (methodNode == null) {
            return false;
        }
        
        List<AnnotationNode> annotations = methodNode.getAnnotations();
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        
        for (AnnotationNode annotation : annotations) {
            ClassNode annotationType = annotation.getClassNode();
            String annotationName = annotationType.getName();
            
            // Check for JUnit or TestNG Test annotations
            if (JUNIT_TEST_ANNOTATION.equals(annotationName) || 
                JUNIT_TEST_ANNOTATION_SHORT.equals(annotationName) ||
                TESTNG_TEST_ANNOTATION.equals(annotationName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Detects if a method is a Spock test method.
     * In Spock, test methods don't need annotations, they're just public methods
     * in a Specification subclass (typically starting with "test" or using "def").
     */
    public static boolean isSpockTestMethod(MethodNode methodNode, ClassNode classNode) {
        if (methodNode == null || !isSpockTest(classNode)) {
            return false;
        }
        
        // In Spock, test methods are typically public methods
        // that aren't constructors or setup/cleanup methods
        String methodName = methodNode.getName();
        
        // Skip special methods
        if (methodName.equals("<init>") || 
            methodName.equals("setup") || 
            methodName.equals("cleanup") ||
            methodName.equals("setupSpec") ||
            methodName.equals("cleanupSpec")) {
            return false;
        }
        
        // Spock test methods are typically public and return void or def
        return methodNode.isPublic() && !methodNode.isStatic();
    }

    /**
     * Detects the test framework for a given class.
     */
    public static TestFramework detectFramework(ClassNode classNode) {
        if (classNode == null) {
            return TestFramework.UNKNOWN;
        }
        
        // Check for Spock first (extends Specification)
        if (isSpockTest(classNode)) {
            return TestFramework.SPOCK;
        }
        
        // Check for JUnit or TestNG by looking for @Test annotations on methods
        List<MethodNode> methods = classNode.getMethods();
        for (MethodNode method : methods) {
            if (isTestMethod(method)) {
                List<AnnotationNode> annotations = method.getAnnotations();
                for (AnnotationNode annotation : annotations) {
                    String annotationName = annotation.getClassNode().getName();
                    if (annotationName.contains("junit")) {
                        return TestFramework.JUNIT;
                    } else if (annotationName.contains("testng")) {
                        return TestFramework.TESTNG;
                    }
                }
            }
        }
        
        return TestFramework.UNKNOWN;
    }

    /**
     * Detects if a class contains any test methods.
     */
    public static boolean hasTestMethods(ClassNode classNode) {
        if (classNode == null) {
            return false;
        }
        
        // Check if it's a Spock test
        if (isSpockTest(classNode)) {
            return true;
        }
        
        // Check for annotated test methods
        List<MethodNode> methods = classNode.getMethods();
        for (MethodNode method : methods) {
            if (isTestMethod(method)) {
                return true;
            }
        }
        
        return false;
    }
}
