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
import findJava from "./utils/findJava";
import * as path from "path";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  Executable,
} from "vscode-languageclient/node";
import { DebugAdapterExecutable } from "vscode";

const MISSING_JAVA_ERROR =
  "Could not locate valid JDK. To configure JDK manually, use the groovy.java.home setting.";
const INVALID_JAVA_ERROR =
  "The groovy.java.home setting does not point to a valid JDK.";
const INITIALIZING_MESSAGE = "Initializing Groovy language server...";
const RELOAD_WINDOW_MESSAGE =
  "To apply new settings for Groovy, please reload the window.";
const STARTUP_ERROR = "The Groovy extension failed to start.";
const LABEL_RELOAD_WINDOW = "Reload Window";
let extensionContext: vscode.ExtensionContext | null = null;
let languageClient: LanguageClient | null = null;
let javaPath: string | null = null;
let debugAdapterExecutable: DebugAdapterExecutable | null = null;
let debugAdapterFactory: vscode.Disposable | null = null;

async function addClasspathJars() {
  const uris = await vscode.window.showOpenDialog({
    canSelectMany: true,
    openLabel: "Add JARs",
    filters: {
      "JAR Files": ["jar"],
    },
  });
  if (!uris || uris.length === 0) {
    return;
  }
  const config = vscode.workspace.getConfiguration("groovy");
  const existing = (config.get("classpath") as string[]) || [];
  const next = new Set(existing);
  uris.forEach((uri) => next.add(uri.fsPath));
  await config.update("classpath", Array.from(next), vscode.ConfigurationTarget.Workspace);
}

async function addMavenDependency() {
  const input = await vscode.window.showInputBox({
    title: "Add Maven Dependency",
    prompt: "Enter Maven coordinate (group:artifact:version[:classifier][@ext])",
    placeHolder: "org.codehaus.groovy:groovy:4.0.26",
    validateInput: (value) => {
      if (!value || !/^[^\s:]+:[^\s:]+:[^\s:]+/.test(value.trim())) {
        return "Expected at least group:artifact:version";
      }
      return null;
    },
  });
  if (!input) {
    return;
  }
  const config = vscode.workspace.getConfiguration("groovy");
  const existing = (config.get("maven.dependencies") as string[]) || [];
  const next = new Set(existing);
  next.add(input.trim());
  await config.update("maven.dependencies", Array.from(next), vscode.ConfigurationTarget.Workspace);
}

async function addMavenRepository() {
  const input = await vscode.window.showInputBox({
    title: "Add Maven Repository",
    prompt: "Enter Maven repository URL",
    placeHolder: "https://repo1.maven.org/maven2",
    validateInput: (value) => {
      if (!value || !/^https?:\/\//.test(value.trim())) {
        return "Expected a valid http(s) URL";
      }
      return null;
    },
  });
  if (!input) {
    return;
  }
  const config = vscode.workspace.getConfiguration("groovy");
  const existing = (config.get("maven.repositories") as string[]) || [];
  const next = new Set(existing);
  next.add(input.trim());
  await config.update("maven.repositories", Array.from(next), vscode.ConfigurationTarget.Workspace);
}

function onDidChangeConfiguration(event: vscode.ConfigurationChangeEvent) {
  if (event.affectsConfiguration("groovy.java.home")) {
    javaPath = findJava();
    //we're going to try to kill the language server and then restart
    //it with the new settings
    restartLanguageServer();
  }
}

function restartLanguageServer() {
  if (!languageClient) {
    startLanguageServer();
    return;
  }
  let oldLanguageClient = languageClient;
  languageClient = null;
  oldLanguageClient.stop().then(
    () => {
      startLanguageServer();
    },
    () => {
      //something went wrong restarting the language server...
      //this shouldn't happen, but if it does, the user can manually restart
      vscode.window
        .showWarningMessage(RELOAD_WINDOW_MESSAGE, LABEL_RELOAD_WINDOW)
        .then((action) => {
          if (action === LABEL_RELOAD_WINDOW) {
            vscode.commands.executeCommand("workbench.action.reloadWindow");
          }
        });
    }
  );
}

export function activate(context: vscode.ExtensionContext) {
  extensionContext = context;
  javaPath = findJava();
  vscode.workspace.onDidChangeConfiguration(onDidChangeConfiguration);

  vscode.commands.registerCommand(
    "groovy.restartServer",
    restartLanguageServer
  );
  vscode.commands.registerCommand(
    "groovy.addClasspathJars",
    addClasspathJars
  );
  vscode.commands.registerCommand(
    "groovy.addMavenDependency",
    addMavenDependency
  );
  vscode.commands.registerCommand(
    "groovy.addMavenRepository",
    addMavenRepository
  );

  startLanguageServer();
}

export function deactivate() {
  if (debugAdapterExecutable) {
    debugAdapterExecutable.dispose();
    debugAdapterExecutable = null;
  }
  if (debugAdapterFactory) {
    debugAdapterFactory.dispose();
    debugAdapterFactory = null;
  }
  extensionContext = null;
}

function startLanguageServer() {
  vscode.window.withProgress(
    { location: vscode.ProgressLocation.Window },
    (progress) => {
      return new Promise<void>(async (resolve, reject) => {
        if (!extensionContext) {
          //something very bad happened!
          resolve();
          vscode.window.showErrorMessage(STARTUP_ERROR);
          return;
        }
        if (!javaPath) {
          resolve();
          let settingsJavaHome = vscode.workspace
            .getConfiguration("groovy")
            .get("java.home") as string;
          if (settingsJavaHome) {
            vscode.window.showErrorMessage(INVALID_JAVA_ERROR);
          } else {
            vscode.window.showErrorMessage(MISSING_JAVA_ERROR);
          }
          return;
        }
        progress.report({ message: INITIALIZING_MESSAGE });
        let clientOptions: LanguageClientOptions = {
          documentSelector: [{ scheme: "file", language: "groovy" }],
          synchronize: {
            configurationSection: "groovy",
          },
          uriConverters: {
            code2Protocol: (value: vscode.Uri) => {
              if (/^win32/.test(process.platform)) {
                //drive letters on Windows are encoded with %3A instead of :
                //but Java doesn't treat them the same
                return value.toString().replace("%3A", ":");
              } else {
                return value.toString();
              }
            },
            //this is just the default behavior, but we need to define both
            protocol2Code: (value) => vscode.Uri.parse(value),
          },
        };
        let args = [
          "-jar",
          path.resolve(
            extensionContext.extensionPath,
            "bin",
            "groovy-language-server-all.jar"
          ),
        ];
        //uncomment to allow a debugger to attach to the language server
        //args.unshift("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005,quiet=y");
        let executable: Executable = {
          command: javaPath,
          args: args,
        };
        languageClient = new LanguageClient(
          "groovy",
          "Groovy Language Server",
          executable,
          clientOptions
        );
        if (debugAdapterExecutable) {
          debugAdapterExecutable.dispose();
        }
        debugAdapterExecutable = new vscode.DebugAdapterExecutable(javaPath, [
          "-cp",
          path.resolve(
            extensionContext.extensionPath,
            "bin",
            "groovy-language-server-all.jar"
          ),
          "net.prominic.groovyls.debug.GroovyDebugAdapterLauncher",
        ]);
        if (!debugAdapterFactory) {
          debugAdapterFactory = vscode.debug.registerDebugAdapterDescriptorFactory(
            "groovy",
            {
              createDebugAdapterDescriptor(): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
                return debugAdapterExecutable;
              },
            }
          );
          extensionContext.subscriptions.push(debugAdapterFactory);
        }
        try {
          await languageClient.start();
        } catch (e) {
          resolve();
          vscode.window.showErrorMessage(STARTUP_ERROR);
          return;
        }
        resolve();
      });
    }
  );
}
