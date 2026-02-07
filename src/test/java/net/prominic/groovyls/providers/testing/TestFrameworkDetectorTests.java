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

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFrameworkDetectorTests {

    @Test
    void testDetectsSpockSpecification() {
        ClassNode specificationClass = new ClassNode("spock.lang.Specification", 0, null);
        ClassNode testClass = new ClassNode("MySpec", 0, specificationClass);
        
        boolean result = TestFrameworkDetector.isSpockTest(testClass);
        
        assertTrue(result, "Should detect Spock Specification");
    }

    @Test
    void testDetectsNonSpockClass() {
        ClassNode testClass = new ClassNode("MyTest", 0, null);
        
        boolean result = TestFrameworkDetector.isSpockTest(testClass);
        
        assertFalse(result, "Should not detect non-Spock class as Spock test");
    }

    @Test
    void testDetectsSpockTestMethod() {
        ClassNode specificationClass = new ClassNode("spock.lang.Specification", 0, null);
        ClassNode testClass = new ClassNode("MySpec", 0, specificationClass);
        testClass.setModifiers(1); // PUBLIC
        
        MethodNode method = new MethodNode("test something", 1, null, new Parameter[0], null, null);
        
        boolean result = TestFrameworkDetector.isSpockTestMethod(method, testClass);
        
        assertTrue(result, "Public method in Spock spec should be detected as test method");
    }

    @Test
    void testDoesNotDetectSpockSetupMethod() {
        ClassNode specificationClass = new ClassNode("spock.lang.Specification", 0, null);
        ClassNode testClass = new ClassNode("MySpec", 0, specificationClass);
        
        MethodNode setupMethod = new MethodNode("setup", 1, null, new Parameter[0], null, null);
        
        boolean result = TestFrameworkDetector.isSpockTestMethod(setupMethod, testClass);
        
        assertFalse(result, "Setup method should not be detected as test method");
    }

    @Test
    void testDetectFrameworkForSpock() {
        ClassNode specificationClass = new ClassNode("spock.lang.Specification", 0, null);
        ClassNode testClass = new ClassNode("MySpec", 0, specificationClass);
        
        TestFramework framework = TestFrameworkDetector.detectFramework(testClass);
        
        assertEquals(TestFramework.SPOCK, framework, "Should detect Spock framework");
    }

    @Test
    void testDetectFrameworkForUnknown() {
        ClassNode testClass = new ClassNode("MyClass", 0, null);
        
        TestFramework framework = TestFrameworkDetector.detectFramework(testClass);
        
        assertEquals(TestFramework.UNKNOWN, framework, "Should return UNKNOWN for non-test class");
    }

    @Test
    void testHasTestMethodsForSpock() {
        ClassNode specificationClass = new ClassNode("spock.lang.Specification", 0, null);
        ClassNode testClass = new ClassNode("MySpec", 0, specificationClass);
        
        boolean result = TestFrameworkDetector.hasTestMethods(testClass);
        
        assertTrue(result, "Spock specification should be detected as having test methods");
    }
}
