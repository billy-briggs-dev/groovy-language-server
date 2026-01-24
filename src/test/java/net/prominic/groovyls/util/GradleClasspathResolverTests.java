////////////////////////////////////////////////////////////////////////////////
// Copyright 2026 Prominic.NET, Inc.
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
package net.prominic.groovyls.util;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GradleClasspathResolverTests {
    @Test
    void mergeClasspathPreservesOrderAndDedupes() {
        List<String> user = Arrays.asList("/path/user.jar", "/path/shared.jar");
        List<String> gradle = Arrays.asList("/path/gradle.jar", "/path/shared.jar");

        List<String> merged = GradleClasspathResolver.mergeClasspath(user, gradle);
        Assertions.assertEquals(3, merged.size());
        Assertions.assertEquals("/path/user.jar", merged.get(0));
        Assertions.assertEquals("/path/shared.jar", merged.get(1));
        Assertions.assertEquals("/path/gradle.jar", merged.get(2));
    }

    @Test
    void resolveReturnsEmptyWhenProjectMissing() {
        List<String> resolved = GradleClasspathResolver.resolve(null);
        Assertions.assertTrue(resolved.isEmpty());
    }
}
