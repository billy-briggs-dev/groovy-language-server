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
package net.prominic.groovyls.compiler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import groovy.lang.groovydoc.Groovydoc;

public class GroovydocUtils {
	// Matches inline tag bodies with optional single-level nested braces.
	private static final String INLINE_TAG_BODY_PATTERN = "[^{}]*(?:\\{[^{}]*\\}[^{}]*)*";
	private static final Pattern INLINE_TAG_PATTERN = Pattern
			.compile("\\{@(link|linkplain|code|literal)\\s+(" + INLINE_TAG_BODY_PATTERN + ")\\}");
	private static final Pattern HTML_LINK_PATTERN = Pattern.compile("<a\\s+href=(\"|')(.*?)\\1\\s*>(.*?)</a>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern LINK_PARTS_PATTERN = Pattern.compile("([^\\s]+)(?:\\s+(.+))?");
	// Matches fully-qualified class names, e.g. com.example.MyClass.
	private static final Pattern CLASS_REFERENCE_PATTERN = Pattern
			.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+");
	private static final String GROOVYDOC_BASE_URL = "https://docs.groovy-lang.org/latest/html/api/";
	private static final String JAVA_DOC_BASE_URL = "https://docs.oracle.com/en/java/javase/"
			+ Runtime.version().feature() + "/docs/api/";
	private static final String[] JAVA_PACKAGE_PREFIXES = { "java.", "javax.", "jakarta." };
	private static final String[] GROOVY_PACKAGE_PREFIXES = { "groovy.", "org.codehaus.groovy.",
			"org.apache.groovy." };

	public static String groovydocToMarkdownDescription(Groovydoc groovydoc) {
		if (groovydoc == null || !groovydoc.isPresent()) {
			return null;
		}
		String content = groovydoc.getContent();
		if (content == null || content.isBlank()) {
			return null;
		}
		String[] lines = content.split("\n");
		StringBuilder markdownBuilder = new StringBuilder();
		List<String> seeAlso = new ArrayList<>();
		int n = lines.length;
		for (int i = 0; i < n; i++) {
			String line = lines[i];
			if (i == 0) {
				line = line.replaceFirst("^\\s*/\\*\\* ?", "");
			}
			if (i == n - 1) {
				line = line.replaceFirst("\\*/\\s*$", "");
			}
			line = line.replaceFirst("^\\s*\\*\\s?", "");
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				appendLine(markdownBuilder, trimmed);
				continue;
			}
			if (trimmed.startsWith("@")) {
				if (trimmed.startsWith("@see")) {
					String seeEntry = trimmed.substring(4).trim();
					if (!seeEntry.isEmpty()) {
						seeAlso.add(formatSeeAlsoEntry(seeEntry));
					}
				}
				continue;
			}
			appendLine(markdownBuilder, line);
		}
		if (!seeAlso.isEmpty()) {
			markdownBuilder.append("\nSee also:\n");
			for (String entry : seeAlso) {
				markdownBuilder.append("- ").append(entry).append("\n");
			}
		}
		return markdownBuilder.toString().trim();
	}

	private static void appendLine(StringBuilder markdownBuilder, String line) {
		line = reformatLine(line);
		if (line.length() == 0) {
			return;
		}
		markdownBuilder.append(line);
		markdownBuilder.append("\n");
	}

	private static String reformatLine(String line) {
		line = replaceInlineTags(line);
		line = replaceHtmlLinks(line);
		// Handle combined <pre><code> before standalone <pre> tags.
		line = line.replaceAll("(?i)<pre>\\s*<code>", "\n\n```\n");
		line = line.replaceAll("(?i)</code>\\s*</pre>", "\n```\n");
		line = line.replaceAll("(?i)<pre>", "\n\n```\n");
		line = line.replaceAll("(?i)</pre>", "\n```\n");
		// remove all attributes (including namespaced)
		line = line.replaceAll("<(\\w+)(?:\\s+\\w+(?::\\w+)?=(\"|\')[^\"\']*\\2)*\\s*(\\/{0,1})>", "<$1$3>");
		line = line.replaceAll("</?(em|i)>", "_");
		line = line.replaceAll("</?(strong|b)>", "**");
		line = line.replaceAll("</?(tt|code)>", "`");
		line = line.replaceAll("(?i)<hr ?\\/>", "\n\n---\n\n");
		line = line.replaceAll("(?i)<(p|ul|ol|dl|li|dt|table|tr|div|blockquote)>", "\n\n");

		// to add a line break to markdown, there needs to be at least two
		// spaces at the end of the line
		line = line.replaceAll("(?i)<br\\s*/?>\\s*", "  \n");
		line = line.replaceAll("<\\/{0,1}\\w+\\/{0,1}>", "");
		line = decodeHtmlEntities(line);
		return line;
	}

	private static String replaceInlineTags(String line) {
		Matcher matcher = INLINE_TAG_PATTERN.matcher(line);
		StringBuilder builder = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			builder.append(line, last, matcher.start());
			String tag = matcher.group(1);
			String body = matcher.group(2).trim();
			String replacement = body;
			if ("code".equals(tag)) {
				replacement = "`" + body + "`";
			} else if ("literal".equals(tag)) {
				replacement = body;
			} else {
				replacement = formatLink(body);
			}
			builder.append(replacement);
			last = matcher.end();
		}
		builder.append(line.substring(last));
		return builder.toString();
	}

	private static String replaceHtmlLinks(String line) {
		Matcher matcher = HTML_LINK_PATTERN.matcher(line);
		StringBuilder builder = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			builder.append(line, last, matcher.start());
			String url = matcher.group(2);
			String label = matcher.group(3);
			builder.append("[").append(label).append("](").append(url).append(")");
			last = matcher.end();
		}
		builder.append(line.substring(last));
		return builder.toString();
	}

	private static String formatSeeAlsoEntry(String entry) {
		String trimmed = entry.trim();
		String link = buildMarkdownLink(trimmed, trimmed);
		if (link != null) {
			return link;
		}
		return reformatLine(trimmed);
	}

	private static String formatLink(String tagBody) {
		Matcher matcher = LINK_PARTS_PATTERN.matcher(tagBody);
		if (!matcher.matches()) {
			return tagBody;
		}
		String reference = matcher.group(1);
		String label = matcher.group(2) != null ? matcher.group(2).trim() : reference;
		String link = buildMarkdownLink(reference, label);
		return link != null ? link : label;
	}

	private static String buildMarkdownLink(String reference, String label) {
		String url = resolveExternalUrl(reference);
		if (url == null) {
			return null;
		}
		return "[" + label + "](" + url + ")";
	}

	private static String resolveExternalUrl(String reference) {
		if (reference.startsWith("http://") || reference.startsWith("https://")) {
			return reference;
		}
		if (reference.startsWith("#")) {
			return null;
		}
		String anchor = null;
		String classRef = reference;
		int hashIndex = reference.indexOf('#');
		if (hashIndex > -1) {
			classRef = reference.substring(0, hashIndex);
			anchor = reference.substring(hashIndex + 1);
		}
		if (!CLASS_REFERENCE_PATTERN.matcher(classRef).matches()) {
			return null;
		}
		String baseUrl;
		if (startsWithAny(classRef, JAVA_PACKAGE_PREFIXES)) {
			baseUrl = JAVA_DOC_BASE_URL;
		} else if (startsWithAny(classRef, GROOVY_PACKAGE_PREFIXES)) {
			baseUrl = GROOVYDOC_BASE_URL;
		} else {
			return null;
		}
		StringBuilder url = new StringBuilder(baseUrl);
		url.append(classRef.replace('.', '/'));
		url.append(".html");
		if (anchor != null && !anchor.isBlank()) {
			url.append("#").append(anchor);
		}
		return url.toString();
	}

	private static String decodeHtmlEntities(String line) {
		return line.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<")
				.replace("&gt;", ">");
	}

	private static boolean startsWithAny(String value, String[] prefixes) {
		for (String prefix : prefixes) {
			if (value.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
}
