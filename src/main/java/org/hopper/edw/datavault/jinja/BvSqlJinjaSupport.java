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
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.interpret.TemplateError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRefResolver;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlTemplateParser;
import org.hopper.edw.datavault.metadata.businessvault.BvTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroDefinition;

/**
 * Renders Business Vault authoring SQL through sandboxed Jinja when the query is more than simple
 * {@code ref()}/{@code source()} macros.
 */
public final class BvSqlJinjaSupport {

  private static final Class<?> PKG = BvSqlJinjaSupport.class;

  private BvSqlJinjaSupport() {}

  /**
   * True when the SQL uses Jinja control tags, comments, or expressions other than the simple
   * single-quoted {@code ref}/{@code source} macros handled by {@link BvSqlTemplateParser}.
   */
  public static boolean needsJinjaRender(String sql) {
    if (Utils.isEmpty(sql)) {
      return false;
    }
    if (sql.contains("{%") || sql.contains("%}") || sql.contains("{#") || sql.contains("#}")) {
      return true;
    }
    String stripped = stripKnownMacros(sql);
    return stripped.contains("{{") || stripped.contains("}}");
  }

  /**
   * Renders a snippet against an explicit macro list (used by the library editor test-render).
   * {@code ref()}/{@code source()} resolve to the object name when no vault models are provided.
   */
  public static String renderSnippet(
      String snippet,
      List<JinjaMacroDefinition> macros,
      Map<String, String> vars,
      IVariables variables)
      throws HopException {
    BvBusinessTable dummy = new BvBusinessTable();
    dummy.setName("snippet");
    dummy.setTableName("snippet");
    dummy.setSqlQuery(prependMacros(macros, snippet != null ? snippet : ""));
    DatabaseMeta none = null;
    BvSqlJinjaRenderSession session =
        new BvSqlJinjaRenderSession(
            dummy,
            null,
            null,
            null,
            variables,
            none,
            none,
            BvSqlJinjaRenderSession.caseInsensitiveVars(vars));
    RenderResult result = renderWithPluginLoader(session, dummy.getSqlQuery(), dummy);
    return result.getOutput() != null ? result.getOutput() : "";
  }

  public static BvSqlJinjaRenderResult render(
      BvBusinessTable table,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DatabaseMeta defaultDatabaseMeta)
      throws HopException {
    if (table == null || Utils.isEmpty(table.getSqlQuery())) {
      return new BvSqlJinjaRenderResult(
          table != null ? table.getSqlQuery() : null, List.of(), List.of());
    }

    DatabaseMeta bvDb = defaultDatabaseMeta;
    if (bvDb == null && metadataProvider != null && bvModel != null) {
      bvDb =
          BvTargetDatabaseSupport.loadTargetDatabase(
              metadataProvider, bvModel.getConfigurationOrDefault());
    }
    DatabaseMeta dvDb = loadDvDatabase(metadataProvider, dvModel);

    JinjaMacroLibraryLoader.LoadedLibraries libraries =
        JinjaMacroLibraryLoader.load(metadataProvider, bvModel);
    BvSqlJinjaRenderSession session =
        new BvSqlJinjaRenderSession(
            table, bvModel, dvModel, metadataProvider, variables, bvDb, dvDb, libraries.vars());

    String template = prependMacros(libraries.macros(), table.getSqlQuery());
    RenderResult result = renderWithPluginLoader(session, template, table);
    String output = result.getOutput();
    return new BvSqlJinjaRenderResult(
        output != null ? output : "",
        List.copyOf(session.getRefs()),
        List.copyOf(session.getSourceUsages()));
  }

  /**
   * Construct and run Jinjava with this plugin as the TCCL so shaded JUEL can load {@code
   * ExtendedSyntaxBuilder} (Hop GUI otherwise uses the application classloader).
   */
  private static RenderResult renderWithPluginLoader(
      BvSqlJinjaRenderSession session, String template, BvBusinessTable table) throws HopException {
    ClassLoader original = JinjaSandboxFactory.installPluginContextClassLoader();
    try {
      Jinjava jinjava = createEngine(tableLabel(table));
      DbtJinjaBuiltins.bind(session);
      try {
        RenderResult result = jinjava.renderForResult(template, buildBindings(session));
        if (result.hasErrors()) {
          throw new HopException(formatErrors(table, result.getErrors()));
        }
        return result;
      } catch (HopException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "BvSqlJinjaSupport.Error.RenderFailed",
                tableLabel(table),
                exceptionMessage(e)),
            e);
      } finally {
        DbtJinjaBuiltins.unbind();
      }
    } finally {
      JinjaSandboxFactory.restoreContextClassLoader(original);
    }
  }

  public static String prependMacros(List<JinjaMacroDefinition> macros, String sql) {
    if (macros == null || macros.isEmpty()) {
      return sql;
    }
    StringBuilder header = new StringBuilder();
    for (JinjaMacroDefinition macro : macros) {
      if (macro == null || Utils.isEmpty(macro.getJinjaSource())) {
        continue;
      }
      String source = macro.getJinjaSource().trim();
      if (containsMacroTag(source)) {
        header.append(source);
        if (!source.endsWith("\n")) {
          header.append('\n');
        }
      } else if (!Utils.isEmpty(macro.getName())) {
        header
            .append("{%- macro ")
            .append(macro.getName())
            .append("() -%}\n")
            .append(source)
            .append("\n{%- endmacro -%}\n");
      }
    }
    header.append(sql);
    return header.toString();
  }

  static boolean containsMacroTag(String source) {
    String lower = source.toLowerCase();
    return lower.contains("{% macro")
        || lower.contains("{%- macro")
        || lower.contains("{%macro")
        || lower.contains("{%-macro");
  }

  private static Map<String, Object> buildBindings(BvSqlJinjaRenderSession session) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put(
        "this",
        BvSqlRefResolver.quoteTable(
            session.getBvDatabase(),
            session.getVariables(),
            session.getTable() != null ? session.getTable().getSchemaName() : null,
            targetName(session.getTable())));
    bindings.put("execute", Boolean.TRUE);
    bindings.put(
        "adapter",
        new DbtJinjaContextObjects.Adapter(session.getBvDatabase(), session.getVariables()));
    bindings.put("exceptions", new DbtJinjaContextObjects.Exceptions());
    bindings.put("graph", new DbtJinjaContextObjects.UnsupportedName("graph"));
    bindings.put("flags", new DbtJinjaContextObjects.UnsupportedName("flags"));
    bindings.put("modules", new DbtJinjaContextObjects.UnsupportedName("modules"));
    return bindings;
  }

  private static DatabaseMeta loadDvDatabase(
      IHopMetadataProvider metadataProvider, DataVaultModel dvModel) {
    if (metadataProvider == null || dvModel == null) {
      return null;
    }
    try {
      return org.hopper.edw.datavault.metadata.DvSpecialRecordSupport.loadTargetDatabase(
          metadataProvider, dvModel.getConfigurationOrDefault());
    } catch (Exception e) {
      return null;
    }
  }

  private static String stripKnownMacros(String sql) {
    List<BvSqlTemplateParser.MacroOccurrence> macros = BvSqlTemplateParser.parse(sql);
    if (macros.isEmpty()) {
      return sql;
    }
    StringBuilder stripped = new StringBuilder(sql);
    for (int i = macros.size() - 1; i >= 0; i--) {
      BvSqlTemplateParser.MacroOccurrence macro = macros.get(i);
      for (int p = macro.start(); p < macro.end(); p++) {
        stripped.setCharAt(p, ' ');
      }
    }
    return stripped.toString();
  }

  private static String formatErrors(BvBusinessTable table, List<TemplateError> errors) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        BaseMessages.getString(PKG, "BvSqlJinjaSupport.Error.RenderFailed", tableLabel(table), ""));
    for (TemplateError error : errors) {
      if (error == null) {
        continue;
      }
      if (sb.charAt(sb.length() - 1) != ' ') {
        sb.append(' ');
      }
      if (error.getLineno() > 0) {
        sb.append('[').append(error.getLineno()).append("] ");
      }
      sb.append(error.getMessage());
    }
    return sb.toString().trim();
  }

  private static String tableLabel(BvBusinessTable table) {
    return table != null && !Utils.isEmpty(table.getName()) ? table.getName() : "?";
  }

  private static String targetName(BvBusinessTable table) {
    if (table == null) {
      return "";
    }
    return !Utils.isEmpty(table.getTableName()) ? table.getTableName() : table.getName();
  }

  /**
   * {@link com.hubspot.jinjava.interpret.ContextConfiguration} class-init needs HubSpot
   * immutables-exceptions on the plugin classpath. A missing jar is {@link LinkageError}, not
   * {@link RuntimeException}.
   */
  private static Jinjava createEngine(String tableLabel) throws HopException {
    try {
      return JinjaSandboxFactory.newEngine();
    } catch (LinkageError e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "BvSqlJinjaSupport.Error.EngineInit", tableLabel, exceptionMessage(e)),
          e);
    }
  }

  private static String exceptionMessage(Throwable e) {
    if (e == null) {
      return "";
    }
    if (!Utils.isEmpty(e.getMessage())) {
      return e.getMessage();
    }
    return e.getClass().getSimpleName();
  }
}
