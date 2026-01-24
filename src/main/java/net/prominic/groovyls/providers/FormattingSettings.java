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

public class FormattingSettings {
	public enum BraceStyle {
		SAME_LINE,
		NEXT_LINE
	}

	private int indentSize = 4;
	private BraceStyle braceStyle = BraceStyle.SAME_LINE;
	private boolean spaceAroundOperators = true;
	private boolean spaceAfterCommas = true;
	private boolean spaceInsideBraces = false;
	private boolean formatOnSave = false;

	public int getIndentSize() {
		return indentSize;
	}

	public void setIndentSize(int indentSize) {
		this.indentSize = indentSize;
	}

	public BraceStyle getBraceStyle() {
		return braceStyle;
	}

	public void setBraceStyle(BraceStyle braceStyle) {
		this.braceStyle = braceStyle;
	}

	public boolean isSpaceAroundOperators() {
		return spaceAroundOperators;
	}

	public void setSpaceAroundOperators(boolean spaceAroundOperators) {
		this.spaceAroundOperators = spaceAroundOperators;
	}

	public boolean isSpaceAfterCommas() {
		return spaceAfterCommas;
	}

	public void setSpaceAfterCommas(boolean spaceAfterCommas) {
		this.spaceAfterCommas = spaceAfterCommas;
	}

	public boolean isSpaceInsideBraces() {
		return spaceInsideBraces;
	}

	public void setSpaceInsideBraces(boolean spaceInsideBraces) {
		this.spaceInsideBraces = spaceInsideBraces;
	}

	public boolean isFormatOnSave() {
		return formatOnSave;
	}

	public void setFormatOnSave(boolean formatOnSave) {
		this.formatOnSave = formatOnSave;
	}
}
