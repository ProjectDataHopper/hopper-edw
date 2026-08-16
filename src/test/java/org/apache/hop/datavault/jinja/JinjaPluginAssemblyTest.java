/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.jinja;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

/**
 * Guards the plugin zip against the Hop GUI failure mode: Jinjava class-init needs HubSpot
 * immutables-exceptions (and a few other compile transitives) that assembly includes must name
 * explicitly.
 */
class JinjaPluginAssemblyTest {

  @Test
  void sandboxEngineConstructs() {
    assertNotNull(JinjaSandboxFactory.newEngine());
  }

  @Test
  void engineAndRenderWorkWhenContextClassLoaderCannotSeeJinjava() throws Exception {
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
      Thread.currentThread().setContextClassLoader(isolated);
      assertNotNull(JinjaSandboxFactory.newEngine());
      String rendered =
          BvSqlJinjaSupport.renderSnippet(
              "{% set x = 1 %}SELECT {{ x }} AS n", List.of(), Map.of(), new Variables());
      assertTrue(rendered.contains("SELECT 1 AS n"));
      assertFalse(rendered.contains("{%"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
    }
  }

  @Test
  void assemblyListsJinjavaRuntimeTransitives() throws Exception {
    Path assembly = Path.of("src/assembly/assembly.xml");
    assertTrue(Files.isRegularFile(assembly), "expected " + assembly.toAbsolutePath());
    String xml = Files.readString(assembly);
    assertTrue(
        xml.contains("<outputDirectory>lib/core</outputDirectory>"),
        "Jinjava runtime jars must unpack into Hop lib/core");
    for (String artifact :
        List.of(
            "com.hubspot.jinjava:jinjava",
            "com.hubspot.immutables:immutables-exceptions",
            "com.hubspot:algebra",
            "ch.obermuhlner:big-math",
            "com.googlecode.java-ipv6:java-ipv6",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")) {
      assertTrue(xml.contains(artifact), "assembly.xml must include " + artifact);
    }
  }

  @Test
  void engineInitClassesAreOnTheTestClasspath() throws ClassNotFoundException {
    Class.forName("com.hubspot.immutables.validation.InvalidImmutableStateException");
    Class.forName("com.hubspot.algebra.Result");
    Class.forName("ch.obermuhlner.math.big.BigDecimalMath");
    Class.forName("com.googlecode.ipv6.IPv6Address");
    Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLMapper");
    Class.forName("com.hubspot.jinjava.lib.filter.FromYamlFilter");
  }
}
