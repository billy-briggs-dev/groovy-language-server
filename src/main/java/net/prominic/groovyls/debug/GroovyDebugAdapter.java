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
package net.prominic.groovyls.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

public class GroovyDebugAdapter implements IDebugProtocolServer {
    private static final int DEFAULT_THREAD_ID = 1;
    private static final int DEFAULT_FRAME_ID = 1;
    private static final int LOCALS_REFERENCE = 1;

    private final AtomicInteger breakpointIdGenerator = new AtomicInteger(1);
    private volatile IDebugProtocolClient client;
    private final List<Breakpoint> breakpoints = new ArrayList<>();
    private final Map<String, String> locals;

    public GroovyDebugAdapter(Map<String, String> locals) {
        this.locals = locals == null ? Collections.emptyMap() : new LinkedHashMap<>(locals);
    }

    public void connect(IDebugProtocolClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        Capabilities capabilities = new Capabilities();
        capabilities.setSupportsConfigurationDoneRequest(true);
        capabilities.setSupportsEvaluateForHovers(true);
        capabilities.setSupportsSetVariable(false);
        capabilities.setSupportsRestartRequest(false);
        capabilities.setSupportsTerminateRequest(true);
        capabilities.setSupportsConditionalBreakpoints(true);
        capabilities.setSupportsValueFormattingOptions(false);
        capabilities.setSupportsExceptionInfoRequest(false);
        capabilities.setSupportsLoadedSourcesRequest(false);
        capabilities.setSupportsModulesRequest(false);
        if (client != null) {
            client.initialized();
        }
        return CompletableFuture.completedFuture(capabilities);
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        sendStoppedEvent("breakpoint");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        sendStoppedEvent("entry");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> attach(Map<String, Object> args) {
        sendStoppedEvent("entry");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        breakpoints.clear();
        Source source = args.getSource();
        SourceBreakpoint[] requested = args.getBreakpoints();
        if (requested != null) {
            for (SourceBreakpoint requestedBreakpoint : requested) {
                breakpoints.add(createBreakpoint(source, requestedBreakpoint.getLine()));
            }
        }
        SetBreakpointsResponse response = new SetBreakpointsResponse();
        response.setBreakpoints(breakpoints.toArray(new Breakpoint[0]));
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        Thread thread = new Thread();
        thread.setId(DEFAULT_THREAD_ID);
        thread.setName("Groovy Main");
        ThreadsResponse response = new ThreadsResponse();
        response.setThreads(new Thread[] { thread });
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        StackFrame frame = new StackFrame();
        frame.setId(DEFAULT_FRAME_ID);
        frame.setName("Groovy Script");
        frame.setLine(1);
        frame.setColumn(1);
        StackTraceResponse response = new StackTraceResponse();
        response.setStackFrames(new StackFrame[] { frame });
        response.setTotalFrames(1);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        Scope scope = new Scope();
        scope.setName("Locals");
        scope.setVariablesReference(LOCALS_REFERENCE);
        scope.setExpensive(false);
        ScopesResponse response = new ScopesResponse();
        response.setScopes(new Scope[] { scope });
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        VariablesResponse response = new VariablesResponse();
        if (args.getVariablesReference() != LOCALS_REFERENCE) {
            response.setVariables(new Variable[0]);
            return CompletableFuture.completedFuture(response);
        }
        List<Variable> variables = new ArrayList<>();
        for (Map.Entry<String, String> entry : locals.entrySet()) {
            Variable variable = new Variable();
            variable.setName(entry.getKey());
            variable.setValue(entry.getValue());
            variable.setType("String");
            variable.setVariablesReference(0);
            variables.add(variable);
        }
        response.setVariables(variables.toArray(new Variable[0]));
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        String expression = args.getExpression();
        String result = locals.getOrDefault(expression, "undefined");
        EvaluateResponse response = new EvaluateResponse();
        response.setResult(result);
        response.setType("String");
        response.setVariablesReference(0);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        ContinueResponse response = new ContinueResponse();
        response.setAllThreadsContinued(true);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        sendTerminatedEvent();
        return CompletableFuture.completedFuture(null);
    }

    private Breakpoint createBreakpoint(Source source, int line) {
        Breakpoint breakpoint = new Breakpoint();
        breakpoint.setId(breakpointIdGenerator.getAndIncrement());
        breakpoint.setVerified(true);
        breakpoint.setSource(source);
        breakpoint.setLine(line);
        return breakpoint;
    }

    private void sendStoppedEvent(String reason) {
        if (client == null) {
            return;
        }
        StoppedEventArguments event = new StoppedEventArguments();
        event.setReason(reason);
        event.setThreadId(DEFAULT_THREAD_ID);
        event.setAllThreadsStopped(true);
        client.stopped(event);
    }

    private void sendTerminatedEvent() {
        if (client == null) {
            return;
        }
        TerminatedEventArguments event = new TerminatedEventArguments();
        client.terminated(event);
    }
}
