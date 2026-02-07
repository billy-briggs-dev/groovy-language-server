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
package net.prominic.groovyls.providers.testing;

import org.eclipse.lsp4j.Range;

public class TestInfo {
    private String name;
    private TestFramework framework;
    private Range range;
    private String className;
    private boolean isTestClass;
    private boolean isTestMethod;

    public TestInfo(String name, TestFramework framework, Range range, String className) {
        this.name = name;
        this.framework = framework;
        this.range = range;
        this.className = className;
        this.isTestClass = false;
        this.isTestMethod = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TestFramework getFramework() {
        return framework;
    }

    public void setFramework(TestFramework framework) {
        this.framework = framework;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public boolean isTestClass() {
        return isTestClass;
    }

    public void setTestClass(boolean isTestClass) {
        this.isTestClass = isTestClass;
    }

    public boolean isTestMethod() {
        return isTestMethod;
    }

    public void setTestMethod(boolean isTestMethod) {
        this.isTestMethod = isTestMethod;
    }
}
