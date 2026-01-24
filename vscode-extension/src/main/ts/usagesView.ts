import * as path from "path";
import * as vscode from "vscode";

export type UsageLocation = {
  uri: string;
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
};

export type UsageItem = {
  type: string;
  location: UsageLocation;
  symbolName?: string | null;
};

export class GroovyUsagesProvider implements vscode.TreeDataProvider<UsageTreeItem> {
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

export class UsageTreeItem extends vscode.TreeItem {
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

export function toVsCodeLocation(location: UsageLocation): vscode.Location {
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
