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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import groovy.lang.groovydoc.Groovydoc;
import groovy.lang.groovydoc.GroovydocHolder;

public class GroovydocUtilsTests {
	private static final class TestGroovydocHolder implements GroovydocHolder<Object> {
		@Override
		public Groovydoc getGroovydoc() {
			return null;
		}

		@Override
		public Object getInstance() {
			return this;
		}
	}

	@Test
	void rendersHtmlAndCodeBlocks() {
		String content = "/**\n" + " * <p>Example</p>\n" + " * <pre><code>println 'hi'</code></pre>\n"
				+ " * <a href=\"https://example.com\">Example link</a>\n" + " */";
		String markdown = GroovydocUtils.groovydocToMarkdownDescription(new Groovydoc(content, new TestGroovydocHolder()));

		Assertions.assertNotNull(markdown);
		Assertions.assertTrue(markdown.contains("Example"));
		Assertions.assertTrue(markdown.contains("```"));
		Assertions.assertTrue(markdown.contains("println 'hi'"));
		Assertions.assertTrue(markdown.contains("[Example link](https://example.com)"));
	}

	@Test
	void rendersInlineLinksAndSeeAlso() {
		String content = "/**\n" + " * Uses {@link java.util.List} and {@link groovy.lang.Closure}.\n"
				+ " * @see java.lang.String\n" + " */";
		String markdown = GroovydocUtils.groovydocToMarkdownDescription(new Groovydoc(content, new TestGroovydocHolder()));

		Assertions.assertNotNull(markdown);
		Assertions.assertTrue(markdown.contains(
				"[java.util.List](https://docs.oracle.com/en/java/javase/21/docs/api/java/util/List.html)"));
		Assertions.assertTrue(markdown.contains(
				"[groovy.lang.Closure](https://docs.groovy-lang.org/latest/html/api/groovy/lang/Closure.html)"));
		Assertions.assertTrue(
				markdown.contains("[java.lang.String](https://docs.oracle.com/en/java/javase/21/docs/api/java/lang/String.html)"));
		Assertions.assertTrue(markdown.contains("See also:"));
	}
}
