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
package net.prominic.groovyls;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.Phases;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.control.GroovyLSCompilationUnit;
import net.prominic.groovyls.config.CompilationUnitFactory;
import net.prominic.groovyls.util.FileContentsTracker;

class ASTTransformationsTests {
    private static final String LANGUAGE_GROOVY = "groovy";
    private static final String PATH_WORKSPACE = "./build/test_workspace/";
    private static final String PATH_SRC = "./src/main/groovy";

    @Test
    void testDelegateTransformAddsMethods() throws Exception {
        String source = String.join("\n",
                "import groovy.lang.Delegate",
                "class Foo { String fooMethod() { '' } }",
                "class Bar { @Delegate Foo foo = new Foo() }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode bar = findClassNode(visitor, "Bar");
        Assertions.assertNotNull(bar);
        Assertions.assertTrue(hasMethod(bar, "fooMethod"));
    }

    @Test
    void testMixinTransformAddsMethods() throws Exception {
        String source = String.join("\n",
                "import groovy.lang.Mixin",
                "class Mix { String mixMethod() { '' } }",
                "@Mixin(Mix)",
                "class Target { }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode target = findClassNode(visitor, "Target");
        Assertions.assertNotNull(target);
        Assertions.assertTrue(hasMethod(target, "mixMethod"));
    }

    @Test
    void testCategoryTransformAddsMethods() throws Exception {
        String source = String.join("\n",
                "import groovy.lang.Category",
                "class Target { }",
                "@Category(Target)",
                "class TargetCategory { static String shout(Target self) { self.toString() } }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode target = findClassNode(visitor, "Target");
        Assertions.assertNotNull(target);
        Assertions.assertTrue(hasMethod(target, "shout"));
    }

    @Test
    void testCanonicalTransformAddsTupleConstructor() throws Exception {
        String source = String.join("\n",
                "import groovy.transform.Canonical",
                "@Canonical",
                "class Person { String name }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode person = findClassNode(visitor, "Person");
        Assertions.assertNotNull(person);
        Assertions.assertTrue(hasConstructorWithParam(person, ClassHelper.STRING_TYPE));
    }

    @Test
    void testImmutableTransformAddsMethods() throws Exception {
        String source = String.join("\n",
                "import groovy.transform.Immutable",
                "@Immutable",
                "class Person { String name }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode person = findClassNode(visitor, "Person");
        Assertions.assertNotNull(person);
        Assertions.assertTrue(hasMethod(person, "copyWith"));
        Assertions.assertTrue(hasMethod(person, "toMap"));
    }

    @Test
    void testBuilderTransformAddsBuilderMethod() throws Exception {
        String source = String.join("\n",
                "import groovy.transform.builder.Builder",
                "@Builder",
                "class Person { String name }");
        ASTNodeVisitor visitor = compileAndVisit(source);
        ClassNode person = findClassNode(visitor, "Person");
        Assertions.assertNotNull(person);
        Assertions.assertTrue(hasStaticMethod(person, "builder"));
    }

    private ASTNodeVisitor compileAndVisit(String source) throws Exception {
        Path workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
        Path srcRoot = workspaceRoot.resolve(PATH_SRC);
        if (!Files.exists(srcRoot)) {
            srcRoot.toFile().mkdirs();
        }
        Path filePath = srcRoot.resolve("Transformations.groovy");
        String uri = filePath.toUri().toString();
        FileContentsTracker tracker = new FileContentsTracker();
        tracker.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, LANGUAGE_GROOVY, 1, source)));
        CompilationUnitFactory factory = new CompilationUnitFactory();
        GroovyLSCompilationUnit unit = factory.create(null, tracker);
        unit.compile(Phases.CANONICALIZATION);
        ASTNodeVisitor visitor = new ASTNodeVisitor();
        visitor.visitCompilationUnit(unit);
        return visitor;
    }

    private ClassNode findClassNode(ASTNodeVisitor visitor, String name) {
        List<ClassNode> classNodes = visitor.getClassNodes();
        for (ClassNode classNode : classNodes) {
            if (name.equals(classNode.getName()) || name.equals(classNode.getNameWithoutPackage())) {
                return classNode;
            }
        }
        return null;
    }

    private boolean hasMethod(ClassNode classNode, String name) {
        return !classNode.getMethods(name).isEmpty();
    }

    private boolean hasStaticMethod(ClassNode classNode, String name) {
        return classNode.getMethods(name).stream().anyMatch(MethodNode::isStatic);
    }

    private boolean hasConstructorWithParam(ClassNode classNode, ClassNode type) {
        for (ConstructorNode constructor : classNode.getDeclaredConstructors()) {
            Parameter[] params = constructor.getParameters();
            if (params.length == 1 && params[0].getType() != null
                    && type.getName().equals(params[0].getType().getName())) {
                return true;
            }
        }
        return false;
    }
}