////////////////////////////////////////////////////////////////////////////////
// Copyright 2026 Prominic.NET, Inc.
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
package net.prominic.groovyls.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GroovyDebugAdapterTests {
    @Test
    void initializesWithCapabilities() throws ExecutionException, InterruptedException {
        GroovyDebugAdapter adapter = new GroovyDebugAdapter(Map.of());
        InitializeRequestArguments args = new InitializeRequestArguments();
        Assertions.assertTrue(adapter.initialize(args).get().getSupportsConfigurationDoneRequest());
        Assertions.assertTrue(adapter.initialize(args).get().getSupportsEvaluateForHovers());
    }

    @Test
    void registersBreakpointsAndVariables() throws ExecutionException, InterruptedException {
        Map<String, String> locals = new LinkedHashMap<>();
        locals.put("name", "groovy");
        GroovyDebugAdapter adapter = new GroovyDebugAdapter(locals);

        Source source = new Source();
        source.setPath("/tmp/sample.groovy");
        SourceBreakpoint breakpoint = new SourceBreakpoint();
        breakpoint.setLine(3);
        SetBreakpointsArguments breakpointArgs = new SetBreakpointsArguments();
        breakpointArgs.setSource(source);
        breakpointArgs.setBreakpoints(new SourceBreakpoint[] { breakpoint });
        SetBreakpointsResponse response = adapter.setBreakpoints(breakpointArgs).get();
        Assertions.assertEquals(1, response.getBreakpoints().length);
        Assertions.assertTrue(response.getBreakpoints()[0].isVerified());

        ScopesArguments scopesArguments = new ScopesArguments();
        int scopeReference = adapter.scopes(scopesArguments).get().getScopes()[0].getVariablesReference();
        VariablesArguments variablesArguments = new VariablesArguments();
        variablesArguments.setVariablesReference(scopeReference);
        VariablesResponse variables = adapter.variables(variablesArguments).get();
        Assertions.assertEquals(1, variables.getVariables().length);
        Assertions.assertEquals("name", variables.getVariables()[0].getName());
        Assertions.assertEquals("groovy", variables.getVariables()[0].getValue());

        EvaluateArguments evaluateArguments = new EvaluateArguments();
        evaluateArguments.setExpression("name");
        Assertions.assertEquals("groovy", adapter.evaluate(evaluateArguments).get().getResult());
    }
}
