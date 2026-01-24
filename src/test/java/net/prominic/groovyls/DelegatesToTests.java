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
import java.util.Optional;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.Phases;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.prominic.groovyls.compiler.ast.ASTNodeVisitor;
import net.prominic.groovyls.compiler.control.GroovyLSCompilationUnit;
import net.prominic.groovyls.compiler.util.GroovyASTUtils;
import net.prominic.groovyls.config.CompilationUnitFactory;
import net.prominic.groovyls.util.FileContentsTracker;

class DelegatesToTests {
    private static final String LANGUAGE_GROOVY = "groovy";
    private static final String PATH_WORKSPACE = "./build/test_workspace/";
    private static final String PATH_SRC = "./src/main/groovy";

    @Test
    void testDelegatesToResolvesItType() throws Exception {
        String source = String.join("\n",
                "import groovy.lang.DelegatesTo",
                "class Foo { String fooMethod() { '' } }",
                "class Util { static void withFoo(@DelegatesTo(Foo) Closure c) { c() } }",
                "class Completion {",
                "  void testMethod() {",
                "    Util.withFoo { it.fooMethod() }",
                "  }",
                "}");
        ASTNodeVisitor visitor = compileAndVisit(source);
        Optional<VariableExpression> itVar = visitor.getNodes().stream()
                .filter(node -> node instanceof VariableExpression)
                .map(node -> (VariableExpression) node)
                .filter(var -> "it".equals(var.getName()))
                .findFirst();
        Assertions.assertTrue(itVar.isPresent());
        ClassNode resolved = GroovyASTUtils.getTypeOfNode(itVar.get(), visitor);
        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("Foo", resolved.getNameWithoutPackage());
    }

    private ASTNodeVisitor compileAndVisit(String source) throws Exception {
        Path workspaceRoot = Paths.get(System.getProperty("user.dir")).resolve(PATH_WORKSPACE);
        Path srcRoot = workspaceRoot.resolve(PATH_SRC);
        if (!Files.exists(srcRoot)) {
            srcRoot.toFile().mkdirs();
        }
        Path filePath = srcRoot.resolve("DelegatesTo.groovy");
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
}
