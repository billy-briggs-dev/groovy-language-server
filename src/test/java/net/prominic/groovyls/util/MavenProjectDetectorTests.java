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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MavenProjectDetectorTests {
    @Test
    void detectReturnsNullWithoutRootPom() throws Exception {
        Path tempDir = Files.createTempDirectory("groovyls-maven");
        MavenProjectInfo info = MavenProjectDetector.detect(tempDir);
        Assertions.assertNull(info);
    }

    @Test
    void detectParsesDependenciesModulesAndRepositories() throws Exception {
        Path tempDir = Files.createTempDirectory("groovyls-maven");
        Path rootPom = tempDir.resolve("pom.xml");
        String rootPomXml = String.join("\n",
                "<project>",
                "  <modelVersion>4.0.0</modelVersion>",
                "  <groupId>com.example</groupId>",
                "  <artifactId>root</artifactId>",
                "  <version>1.0.0</version>",
                "  <properties>",
                "    <junit.version>5.11.4</junit.version>",
                "  </properties>",
                "  <dependencyManagement>",
                "    <dependencies>",
                "      <dependency>",
                "        <groupId>org.slf4j</groupId>",
                "        <artifactId>slf4j-api</artifactId>",
                "        <version>2.0.13</version>",
                "      </dependency>",
                "    </dependencies>",
                "  </dependencyManagement>",
                "  <dependencies>",
                "    <dependency>",
                "      <groupId>org.junit.jupiter</groupId>",
                "      <artifactId>junit-jupiter-api</artifactId>",
                "      <version>${junit.version}</version>",
                "    </dependency>",
                "  </dependencies>",
                "  <repositories>",
                "    <repository>",
                "      <id>central</id>",
                "      <url>https://repo1.maven.org/maven2</url>",
                "    </repository>",
                "  </repositories>",
                "  <modules>",
                "    <module>module-a</module>",
                "  </modules>",
                "</project>");
        Files.writeString(rootPom, rootPomXml);

        Path moduleDir = tempDir.resolve("module-a");
        Files.createDirectories(moduleDir);
        Path modulePom = moduleDir.resolve("pom.xml");
        String modulePomXml = String.join("\n",
                "<project>",
                "  <modelVersion>4.0.0</modelVersion>",
                "  <parent>",
                "    <groupId>com.example</groupId>",
                "    <artifactId>root</artifactId>",
                "    <version>1.0.0</version>",
                "  </parent>",
                "  <artifactId>module-a</artifactId>",
                "  <dependencies>",
                "    <dependency>",
                "      <groupId>org.slf4j</groupId>",
                "      <artifactId>slf4j-api</artifactId>",
                "    </dependency>",
                "  </dependencies>",
                "</project>");
        Files.writeString(modulePom, modulePomXml);

        MavenProjectInfo info = MavenProjectDetector.detect(tempDir);
        Assertions.assertNotNull(info);
        Assertions.assertTrue(info.isMultiModule());
        Assertions.assertEquals(2, info.getPomFiles().size());
        Assertions.assertTrue(info.getModuleDirectories().contains(moduleDir));

        List<String> deps = info.getDependencies();
        Assertions.assertTrue(deps.contains("org.junit.jupiter:junit-jupiter-api:5.11.4"));
        Assertions.assertTrue(deps.contains("org.slf4j:slf4j-api:2.0.13"));

        List<String> repos = info.getRepositories();
        Assertions.assertTrue(repos.contains("https://repo1.maven.org/maven2"));
    }
}
