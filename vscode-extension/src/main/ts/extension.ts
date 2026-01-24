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
let usagesProvider: GroovyUsagesProvider | null = null;

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

  usagesProvider = new GroovyUsagesProvider();
  context.subscriptions.push(
    vscode.window.createTreeView("groovyUsages", {
      treeDataProvider: usagesProvider,
      showCollapseAll: true,
    })
  );

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
  vscode.commands.registerCommand("groovy.findUsages", findUsages);
  vscode.commands.registerCommand("groovy.goToSuperMethod", goToSuperMethod);
  vscode.commands.registerCommand("groovy.openUsage", openUsage);

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

type UsageLocation = {
  uri: string;
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
};

type UsageItem = {
  type: string;
  location: UsageLocation;
  symbolName?: string | null;
};

class GroovyUsagesProvider
  implements vscode.TreeDataProvider<UsageTreeItem>
{
  private readonly _onDidChangeTreeData = new vscode.EventEmitter<
    UsageTreeItem | undefined
  >();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private items: UsageItem[] = [];

  setItems(items: UsageItem[]) {
    this.items = items;
    this._onDidChangeTreeData.fire(undefined);
  }

  clear() {
    this.items = [];
    this._onDidChangeTreeData.fire(undefined);
  }

  getTreeItem(element: UsageTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(element?: UsageTreeItem): vscode.ProviderResult<UsageTreeItem[]> {
    if (!element) {
      return this.getTypeGroups().map(
        (group) =>
          new UsageTreeItem(
            group.typeLabel,
            vscode.TreeItemCollapsibleState.Expanded,
            undefined,
            group.items
          )
      );
    }

    if (element.children) {
      return element.children.map((usage) => {
        const location = toVsCodeLocation(usage.location);
        const label = formatUsageLabel(usage, location);
        const item = new UsageTreeItem(
          label,
          vscode.TreeItemCollapsibleState.None,
          usage,
          undefined
        );
        item.command = {
          command: "groovy.openUsage",
          title: "Open Usage",
          arguments: [usage],
        };
        item.resourceUri = location.uri;
        return item;
      });
    }

    return [];
  }

  private getTypeGroups(): Array<{ typeLabel: string; items: UsageItem[] }> {
    const groups = new Map<string, UsageItem[]>();
    for (const item of this.items) {
      const label = formatTypeLabel(item.type);
      const existing = groups.get(label) ?? [];
      existing.push(item);
      groups.set(label, existing);
    }
    return Array.from(groups.entries()).map(([typeLabel, items]) => ({
      typeLabel,
      items,
    }));
  }
}

class UsageTreeItem extends vscode.TreeItem {
  constructor(
    label: string,
    collapsibleState: vscode.TreeItemCollapsibleState,
    public readonly usage?: UsageItem,
    public readonly children?: UsageItem[]
  ) {
    super(label, collapsibleState);
    if (children) {
      this.contextValue = "groovyUsageGroup";
    } else if (usage) {
      this.contextValue = "groovyUsageItem";
    }
  }
}

function toVsCodeLocation(location: UsageLocation): vscode.Location {
  const uri = vscode.Uri.parse(location.uri);
  const range = new vscode.Range(
    location.range.start.line,
    location.range.start.character,
    location.range.end.line,
    location.range.end.character
  );
  return new vscode.Location(uri, range);
}

function formatTypeLabel(type: string): string {
  switch (type) {
    case "methodCall":
      return "Method Calls";
    case "constructorCall":
      return "Constructor Calls";
    case "fieldAccess":
      return "Field Access";
    case "propertyAccess":
      return "Property Access";
    case "variableReference":
      return "Variable References";
    case "typeReference":
      return "Type References";
    case "declaration":
      return "Declarations";
    default:
      return "References";
  }
}

function formatUsageLabel(usage: UsageItem, location: vscode.Location): string {
  const fileName = path.basename(location.uri.fsPath);
  const line = location.range.start.line + 1;
  const column = location.range.start.character + 1;
  const name = usage.symbolName ? ` ${usage.symbolName}` : "";
  return `${fileName}:${line}:${column}${name}`;
}

async function findUsages() {
  if (!languageClient || !usagesProvider) {
    return;
  }
  const editor = vscode.window.activeTextEditor;
  if (!editor) {
    return;
  }
  await languageClient.onReady();

  const filters = await pickUsageFilters();
  const textDocument = languageClient.code2ProtocolConverter.asTextDocumentIdentifier(
    editor.document
  );
  const position = languageClient.code2ProtocolConverter.asPosition(
    editor.selection.active
  );

  const payload = {
    textDocument,
    position,
    filters,
  };

  const result = (await languageClient.sendRequest("workspace/executeCommand", {
    command: "groovy.findUsages",
    arguments: [payload],
  })) as UsageItem[];

  usagesProvider.setItems(result ?? []);
  if (!result || result.length === 0) {
    vscode.window.showInformationMessage("No usages found.");
  }
}

async function goToSuperMethod() {
  if (!languageClient) {
    return;
  }
  const editor = vscode.window.activeTextEditor;
  if (!editor) {
    return;
  }
  await languageClient.onReady();

  const textDocument = languageClient.code2ProtocolConverter.asTextDocumentIdentifier(
    editor.document
  );
  const position = languageClient.code2ProtocolConverter.asPosition(
    editor.selection.active
  );
  const payload = { textDocument, position };

  const locations = (await languageClient.sendRequest(
    "workspace/executeCommand",
    {
      command: "groovy.goToSuperMethod",
      arguments: [payload],
    }
  )) as { uri: string; range: any }[];

  if (!locations || locations.length === 0) {
    vscode.window.showInformationMessage("No super method found.");
    return;
  }

  if (locations.length === 1) {
    const location = toVsCodeLocation(locations[0] as any);
    await vscode.window.showTextDocument(location.uri, {
      selection: location.range,
    });
    return;
  }

  const picks = locations.map((loc, index) => {
    const location = toVsCodeLocation(loc as any);
    return {
      label: `${location.uri.fsPath}:${location.range.start.line + 1}`,
      location,
      index,
    };
  });
  const choice = await vscode.window.showQuickPick(picks, {
    title: "Select super method",
  });
  if (!choice) {
    return;
  }
  await vscode.window.showTextDocument(choice.location.uri, {
    selection: choice.location.range,
  });
}

function openUsage(usage: UsageItem) {
  const location = toVsCodeLocation(usage.location);
  vscode.window.showTextDocument(location.uri, {
    selection: location.range,
  });
}

async function pickUsageFilters(): Promise<string[]> {
  const picks = await vscode.window.showQuickPick(
    [
      { label: "Method Calls", value: "methodCall" },
      { label: "Constructor Calls", value: "constructorCall" },
      { label: "Field Access", value: "fieldAccess" },
      { label: "Property Access", value: "propertyAccess" },
      { label: "Variable References", value: "variableReference" },
      { label: "Type References", value: "typeReference" },
      { label: "Declarations", value: "declaration" },
      { label: "Other References", value: "reference" },
    ],
    {
      canPickMany: true,
      placeHolder: "Filter usages by type (leave empty for all)",
    }
  );

  if (!picks || picks.length === 0) {
    return [];
  }
  return picks.map((pick) => pick.value);
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
