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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

public final class GroovyDebugAdapterLauncher {
    public static void main(String[] args) {
        InputStream systemIn = System.in;
        OutputStream systemOut = System.out;
        System.setOut(new PrintStream(System.err));
        Map<String, String> locals = new LinkedHashMap<>();
        locals.put("example", "Groovy Debugging Ready");
        locals.put("greeting", "hello");
        GroovyDebugAdapter server = new GroovyDebugAdapter(locals);
        Launcher<IDebugProtocolClient> launcher = DSPLauncher.createServerLauncher(server, systemIn, systemOut);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
