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
package net.prominic.groovyls.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;

import net.prominic.groovyls.util.FileContentsTracker;

public class FormattingProvider {
    private static final String[] OPERATORS = new String[] { ">>>=", "<<=", ">>=", "==", "!=", "<=", ">=", "&&",
            "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", ">>>", "<<", ">>", "->", "=", "+", "-",
            "*", "/", "%", "<", ">", "&", "|", "^", "?", ":" };
    private static final String LINE_BREAK = "\n";
    private static final Set<String> NO_SPACE_OPERATORS = Set.of("++", "--");

	private final FileContentsTracker files;
	private final FormattingSettings settings;

	public FormattingProvider(FileContentsTracker files, FormattingSettings settings) {
		this.files = files;
		this.settings = settings;
	}

	public CompletableFuture<List<TextEdit>> provideDocumentFormatting(TextDocumentIdentifier textDocument) {
		List<TextEdit> edits = formatDocument(textDocument, null);
		return CompletableFuture.completedFuture(edits);
	}

	public CompletableFuture<List<TextEdit>> provideRangeFormatting(DocumentRangeFormattingParams params) {
		List<TextEdit> edits = formatDocument(params.getTextDocument(), params.getRange());
		return CompletableFuture.completedFuture(edits);
	}

	private List<TextEdit> formatDocument(TextDocumentIdentifier textDocument, Range range) {
		URI uri = URI.create(textDocument.getUri());
		String contents = files.getContents(uri);
		if (contents == null) {
			return Collections.emptyList();
		}
		List<String> lines = splitLines(contents);
		if (lines.isEmpty()) {
			return Collections.emptyList();
		}
		int startLine = 0;
		int endLine = lines.size() - 1;
		if (range != null) {
			startLine = Math.max(0, Math.min(range.getStart().getLine(), endLine));
			endLine = Math.max(startLine, Math.min(range.getEnd().getLine(), endLine));
		}
		int indentLevel = calculateIndentLevel(lines, startLine);
		List<String> formattedLines = new ArrayList<>();
		for (int i = startLine; i <= endLine; i++) {
			String line = lines.get(i);
			List<String> lineSegments = formatLine(line);
			for (String segment : lineSegments) {
				if (segment.isBlank()) {
					formattedLines.add("");
					continue;
				}
				boolean startsWithCloseBrace = segment.stripLeading().startsWith("}");
				int indentToUse = indentLevel - (startsWithCloseBrace ? 1 : 0);
				if (indentToUse < 0) {
					indentToUse = 0;
				}
				formattedLines.add(createIndent(indentToUse) + segment.strip());
				indentLevel = updateIndentLevel(segment, indentLevel);
			}
		}
		String formattedText = String.join(LINE_BREAK, formattedLines);
		Range editRange = createRangeForLines(lines, startLine, endLine);
		TextEdit edit = new TextEdit(editRange, formattedText);
		return Collections.singletonList(edit);
	}

	private List<String> formatLine(String line) {
		String trimmed = line.strip();
		if (trimmed.isEmpty()) {
			return Collections.singletonList("");
		}
		List<String> segments = applyBraceStyle(trimmed);
		List<String> formatted = new ArrayList<>();
		for (String segment : segments) {
			String adjusted = applySpacingRules(segment);
			adjusted = applyBraceSpacing(adjusted);
			formatted.add(adjusted.strip());
		}
		return formatted;
	}

	private List<String> applyBraceStyle(String line) {
		if (settings.getBraceStyle() == FormattingSettings.BraceStyle.SAME_LINE) {
			return Collections.singletonList(line);
		}
		int braceIndex = findBraceIndex(line);
		if (braceIndex <= 0) {
			return Collections.singletonList(line);
		}
		String before = line.substring(0, braceIndex).strip();
		String after = line.substring(braceIndex + 1).strip();
		List<String> result = new ArrayList<>();
		if (!before.isEmpty()) {
			result.add(before);
		}
		result.add("{");
		if (!after.isEmpty()) {
			result.add(after);
		}
		return result;
	}

	private int findBraceIndex(String line) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '\\') {
				if (i + 1 < line.length()) {
					i++;
				}
				continue;
			}
			if (character == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
				continue;
			}
			if (character == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
				continue;
			}
			if (inSingleQuote || inDoubleQuote) {
				continue;
			}
			if (character == '{') {
				return i;
			}
		}
		return -1;
	}

	private String applySpacingRules(String line) {
		if (!settings.isSpaceAfterCommas() && !settings.isSpaceAroundOperators()) {
			return line;
		}
		StringBuilder builder = new StringBuilder();
		int index = 0;
		while (index < line.length()) {
			char character = line.charAt(index);
			if (character == '\'' || character == '"') {
				index = appendStringLiteral(line, index, builder);
				continue;
			}
			if (character == '/' && index + 1 < line.length()) {
				char next = line.charAt(index + 1);
				if (next == '/' || next == '*') {
					builder.append(line.substring(index));
					break;
				}
			}
			if (character == ',' && settings.isSpaceAfterCommas()) {
				builder.append(',');
				index++;
				while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
					index++;
				}
				if (index < line.length()) {
					char next = line.charAt(index);
					if (next != ')' && next != ']' && next != '}') {
						builder.append(' ');
					}
				}
				continue;
			}
			String operator = matchOperator(line, index);
			if (operator != null) {
				if (settings.isSpaceAroundOperators() && shouldSpaceOperator(operator)) {
					trimTrailingWhitespace(builder);
					builder.append(' ').append(operator).append(' ');
					index += operator.length();
					while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
						index++;
					}
				} else {
					builder.append(operator);
					index += operator.length();
				}
				continue;
			}
			builder.append(character);
			index++;
		}
		return builder.toString().stripTrailing();
	}

	private String applyBraceSpacing(String line) {
		if (line.indexOf('{') == -1 && line.indexOf('}') == -1) {
			return line;
		}
		if (hasBraceInsideString(line)) {
			return line;
		}
		if (settings.isSpaceInsideBraces()) {
			String adjusted = line.replaceAll("\\{\\s*}", "{}");
			adjusted = adjusted.replaceAll("\\{\\s+", "{ ");
			adjusted = adjusted.replaceAll("\\s+}", " }");
			return adjusted.replaceAll("\\{ \\}", "{}");
		}
		String adjusted = line.replaceAll("\\{\\s+", "{");
		return adjusted.replaceAll("\\s+}", "}");
	}

	private int appendStringLiteral(String line, int startIndex, StringBuilder builder) {
		char quote = line.charAt(startIndex);
		builder.append(quote);
		int index = startIndex + 1;
		while (index < line.length()) {
			char character = line.charAt(index);
			builder.append(character);
			if (character == '\\') {
				if (index + 1 < line.length()) {
					builder.append(line.charAt(index + 1));
					index += 2;
					continue;
				}
				return index + 1;
			}
			if (character == quote) {
				return index + 1;
			}
			index++;
		}
		return index;
	}

	private boolean hasBraceInsideString(String line) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '\\') {
				if (i + 1 < line.length()) {
					i++;
				}
				continue;
			}
			if (character == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
				continue;
			}
			if (character == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
				continue;
			}
			if (inSingleQuote || inDoubleQuote) {
				if (character == '{' || character == '}') {
					return true;
				}
			}
		}
		return false;
	}

	private String matchOperator(String line, int index) {
		for (String operator : OPERATORS) {
			if (line.startsWith(operator, index)) {
				return operator;
			}
		}
		return null;
	}

	private boolean shouldSpaceOperator(String operator) {
		return !NO_SPACE_OPERATORS.contains(operator);
	}

	private void trimTrailingWhitespace(StringBuilder builder) {
		while (builder.length() > 0 && Character.isWhitespace(builder.charAt(builder.length() - 1))) {
			builder.deleteCharAt(builder.length() - 1);
		}
	}

	private String createIndent(int level) {
		int size = settings.getIndentSize();
		if (size <= 0) {
			size = 1;
		}
		return " ".repeat(level * size);
	}

	private int calculateIndentLevel(List<String> lines, int endLine) {
		int indentLevel = 0;
		for (int i = 0; i < endLine; i++) {
			indentLevel = updateIndentLevel(lines.get(i), indentLevel);
		}
		return indentLevel;
	}

	private int updateIndentLevel(String line, int indentLevel) {
		int openCount = 0;
		int closeCount = 0;
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '\\') {
				if (i + 1 < line.length()) {
					i++;
				}
				continue;
			}
			if (character == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
				continue;
			}
			if (character == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
				continue;
			}
			if (inSingleQuote || inDoubleQuote) {
				continue;
			}
			if (character == '{') {
				openCount++;
			} else if (character == '}') {
				closeCount++;
			}
		}
		int nextIndent = indentLevel + openCount - closeCount;
		return Math.max(nextIndent, 0);
	}

	private Range createRangeForLines(List<String> lines, int startLine, int endLine) {
		int endChar = lines.get(endLine).length();
		Position start = new Position(startLine, 0);
		Position end = new Position(endLine, endChar);
		return new Range(start, end);
	}

	private List<String> splitLines(String contents) {
		String[] split = contents.split(LINE_BREAK, -1);
		List<String> lines = new ArrayList<>();
		Collections.addAll(lines, split);
		if (lines.isEmpty()) {
			lines.add("");
		}
		return lines;
	}
}
