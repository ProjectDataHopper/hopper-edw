/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.edw.datavault.jinja;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.lib.fn.ELFunctionDefinition;
import java.util.Set;

/**
 * Builds a sandboxed {@link Jinjava} instance for Business Vault SQL templates.
 *
 * <p>Does not register a file resource locator. Templates cannot {@code include} arbitrary paths.
 */
public final class JinjaSandboxFactory {

  static final int MAX_RENDER_DEPTH = 20;
  static final int MAX_MACRO_RECURSION = 16;
  static final long MAX_OUTPUT_BYTES = 2_000_000L;
  static final long MAX_STRING_LENGTH = 500_000L;
  static final int MAX_LIST_SIZE = 2_000;
  static final int MAX_MAP_SIZE = 2_000;
  static final int RANGE_LIMIT = 500;

  private static final Set<String> RESTRICTED_METHODS =
      Set.of(
          "getClass",
          "getClassLoader",
          "getProtectionDomain",
          "getDeclaredMethods",
          "getDeclaredFields",
          "getDeclaredConstructors",
          "wait",
          "notify",
          "notifyAll");

  private static final Set<String> RESTRICTED_PROPERTIES =
      Set.of("class", "classLoader", "protectionDomain");

  private JinjaSandboxFactory() {}

  /**
   * JUEL {@code ExpressionFactoryImpl} loads {@code com.hubspot.jinjava.el.ExtendedSyntaxBuilder}
   * via {@link Thread#getContextClassLoader()}. In Hop GUI that is the application loader, which
   * cannot see this plugin. Same pattern as Iceberg table reads.
   */
  static ClassLoader installPluginContextClassLoader() {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    thread.setContextClassLoader(JinjaSandboxFactory.class.getClassLoader());
    return original;
  }

  static void restoreContextClassLoader(ClassLoader original) {
    Thread.currentThread().setContextClassLoader(original);
  }

  public static Jinjava newEngine() {
    ClassLoader original = installPluginContextClassLoader();
    try {
      return newEngineUnbound();
    } finally {
      restoreContextClassLoader(original);
    }
  }

  private static Jinjava newEngineUnbound() {
    JinjavaConfig config =
        JinjavaConfig.newBuilder()
            .withFailOnUnknownTokens(true)
            .withReadOnlyResolver(true)
            .withMaxRenderDepth(MAX_RENDER_DEPTH)
            .withMaxOutputSize(MAX_OUTPUT_BYTES)
            .withMaxStringLength(MAX_STRING_LENGTH)
            .withMaxListSize(MAX_LIST_SIZE)
            .withMaxMapSize(MAX_MAP_SIZE)
            .withRangeLimit(RANGE_LIMIT)
            .withEnableRecursiveMacroCalls(true)
            .withMaxMacroRecursionDepth(MAX_MACRO_RECURSION)
            // dbt-core's Environment does not set trim_blocks / lstrip_blocks (Jinja2
            // defaults: off). Enabling them glued FROM onto the last {% endfor %} line.
            .withTrimBlocks(false)
            .withLstripBlocks(false)
            .withKeepTrailingNewline(true)
            .withNestedInterpretationEnabled(false)
            .withRestrictedMethods(RESTRICTED_METHODS)
            .withRestrictedProperties(RESTRICTED_PROPERTIES)
            .build();

    Jinjava jinjava = new Jinjava(config);
    registerBuiltinFunctions(jinjava);
    return jinjava;
  }

  static void registerBuiltinFunctions(Jinjava jinjava) {
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "ref", DbtJinjaBuiltins.class, "ref", Object.class, Object[].class));
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "source", DbtJinjaBuiltins.class, "source", Object.class, Object.class));
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "config", DbtJinjaBuiltins.class, "config", Object[].class));
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "var", DbtJinjaBuiltins.class, "var", Object.class, Object[].class));
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "is_incremental", DbtJinjaBuiltins.class, "is_incremental"));
    jinjava
        .getGlobalContext()
        .registerFunction(
            new ELFunctionDefinition(
                "", "run_query", DbtJinjaBuiltins.class, "run_query", Object[].class));
  }
}
