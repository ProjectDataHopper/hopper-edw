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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRefResolver;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlResolvedKind;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlSource;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BvSqlJinjaSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void simpleRefAndSourceDoesNotNeedJinja() {
    assertFalse(
        BvSqlJinjaSupport.needsJinjaRender(
            "SELECT * FROM {{ ref('s_product') }} JOIN {{ source('ref', 't') }}"));
  }

  @Test
  void controlTagsNeedJinja() {
    assertTrue(BvSqlJinjaSupport.needsJinjaRender("{% set x = 1 %} SELECT {{ x }}"));
    assertTrue(BvSqlJinjaSupport.needsJinjaRender("SELECT {# note #} 1"));
    assertTrue(BvSqlJinjaSupport.needsJinjaRender("SELECT {{ cents_to_dollars('amt') }}"));
  }

  @Test
  void rendersForLoopAndSet() throws Exception {
    String sql =
        """
        {% set methods = ['card', 'cash'] %}
        SELECT
        {% for m in methods %}
          '{{ m }}' AS {{ m }}_lbl{% if not loop.last %},{% endif %}
        {% endfor %}
        """;
    String rendered = BvSqlJinjaSupport.renderSnippet(sql, List.of(), Map.of(), new Variables());
    assertTrue(rendered.contains("'card' AS card_lbl"));
    assertTrue(rendered.contains("'cash' AS cash_lbl"));
    assertFalse(rendered.contains("{%"));
  }

  @Test
  void endforKeepsNewlineLikeDbtCore() throws Exception {
    String typicalDbt =
        """
        {% set cols = ['customer_hk', 'x_to_ts'] %}
        SELECT
        {% for c in cols %}
          {{ c }}{% if not loop.last %},{% endif %}
        {% endfor %}
        FROM {{ ref('sat_customer_hb') }}
        """;
    String typical =
        BvSqlJinjaSupport.renderSnippet(typicalDbt, List.of(), Map.of(), new Variables());
    assertFalse(typical.contains("x_to_tsFROM"), typical);
    assertTrue(typical.matches("(?s).*x_to_ts\\s+FROM sat_customer_hb.*"), typical);

    String extraBlank =
        """
        {% set cols = ['customer_hk', 'x_to_ts'] %}
        SELECT
        {% for c in cols %}
          {{ c }}{% if not loop.last %},{% endif %}
        {% endfor %}

        FROM {{ ref('sat_customer_hb') }}
        """;
    String extra =
        BvSqlJinjaSupport.renderSnippet(extraBlank, List.of(), Map.of(), new Variables());
    assertFalse(extra.contains("x_to_tsFROM"), extra);
    assertTrue(extra.matches("(?s).*x_to_ts\\s+FROM sat_customer_hb.*"), extra);
  }

  @Test
  void rendersUserMacro() throws Exception {
    JinjaMacroDefinition macro = new JinjaMacroDefinition();
    macro.setName("cents_to_dollars");
    macro.setJinjaSource(
        "{%- macro cents_to_dollars(col) -%}(({{ col }}) / 100.0){%- endmacro -%}");
    String rendered =
        BvSqlJinjaSupport.renderSnippet(
            "SELECT {{ cents_to_dollars('amount') }} AS dollars",
            List.of(macro),
            Map.of(),
            new Variables());
    assertTrue(rendered.contains("((amount) / 100.0)"));
    assertFalse(rendered.contains("cents_to_dollars"));
  }

  @Test
  void varPrefersHopVariableOverLibraryDefault() throws Exception {
    Variables vars = new Variables();
    vars.setVariable("region", "hop-region");
    String rendered =
        BvSqlJinjaSupport.renderSnippet(
            "SELECT '{{ var('region', 'lib') }}' AS r", List.of(), Map.of("region", "lib"), vars);
    assertTrue(rendered.contains("'hop-region'"));
  }

  @Test
  void varUsesLibraryDefaultThenExplicitDefault() throws Exception {
    String fromLib =
        BvSqlJinjaSupport.renderSnippet(
            "SELECT '{{ var('missing', 'fallback') }}' AS r", List.of(), Map.of(), new Variables());
    assertTrue(fromLib.contains("'fallback'"));

    String fromMap =
        BvSqlJinjaSupport.renderSnippet(
            "SELECT '{{ var('region') }}' AS r",
            List.of(),
            Map.of("region", "emea"),
            new Variables());
    assertTrue(fromMap.contains("'emea'"));
  }

  @Test
  void missingVarWithoutDefaultFails() {
    assertThrows(
        HopException.class,
        () ->
            BvSqlJinjaSupport.renderSnippet(
                "SELECT '{{ var('nope') }}'", List.of(), Map.of(), new Variables()));
  }

  @Test
  void configIsStrippedAndThisIsQuoted() throws Exception {
    BvBusinessTable table = new BvBusinessTable();
    table.setName("customers");
    table.setTableName("customers");
    table.setSchemaName("marts");
    table.setSqlQuery("{{ config(materialized='table') }}SELECT * FROM {{ this }}");
    BvSqlJinjaRenderResult result =
        BvSqlJinjaSupport.render(
            table, new BusinessVaultModel(), new DataVaultModel(), null, new Variables(), null);
    assertFalse(result.renderedSql().contains("config"));
    assertTrue(result.renderedSql().contains("marts.customers"));
    assertTrue(result.renderedSql().contains("SELECT"));
  }

  @Test
  void refCollectsDynamicObjectName() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("sat_customer");
    scd2.setTableName("sat_customer");
    bv.getTables().add(scd2);

    BvBusinessTable table = new BvBusinessTable();
    table.setName("v1");
    table.setTableName("v1");
    table.setSqlQuery("{% set t = 'sat_customer' %}SELECT * FROM {{ ref(t) }}");

    BvSqlJinjaRenderResult result =
        BvSqlJinjaSupport.render(table, bv, new DataVaultModel(), null, new Variables(), null);
    assertEquals(1, result.refs().size());
    assertEquals("sat_customer", result.refs().get(0).getObjectName());
    assertEquals(BvSqlResolvedKind.BV_TABLE, result.refs().get(0).getResolvedKind());
    assertTrue(result.renderedSql().contains("sat_customer"));
    assertFalse(result.renderedSql().contains("{{"));
  }

  @Test
  void resolveSqlUsesJinjaPath() throws Exception {
    BusinessVaultModel bv = new BusinessVaultModel();
    BvScd2Table scd2 = new BvScd2Table();
    scd2.setName("s_product");
    scd2.setTableName("s_product");
    bv.getTables().add(scd2);

    BvBusinessTable table = new BvBusinessTable();
    table.setName("p");
    table.setTableName("p");
    table.setSqlQuery("{% set x = 1 %}SELECT {{ x }} FROM {{ ref('s_product') }}");

    String resolved =
        BvSqlRefResolver.resolveSql(table, bv, new DataVaultModel(), null, new Variables(), null);
    assertTrue(resolved.contains("SELECT 1"));
    assertTrue(resolved.contains("s_product"));
    assertFalse(resolved.contains("{%"));
  }

  @Test
  void sourceIsCollectedAndQuotedWithSchema() throws Exception {
    BvBusinessTable table = new BvBusinessTable();
    table.setName("v1");
    table.setTableName("v1");
    table.setSqlQuery("SELECT * FROM {{ source('refdata', 'lookup') }}");
    table.getSources().add(new BvSqlSource("refdata", null, "ref", "lookup"));

    BvSqlJinjaRenderResult result =
        BvSqlJinjaSupport.render(
            table, new BusinessVaultModel(), new DataVaultModel(), null, new Variables(), null);
    assertEquals(1, result.sourceUsages().size());
    assertEquals("refdata", result.sourceUsages().get(0).getSourceName());
    assertTrue(result.renderedSql().contains("ref.lookup"));
  }

  @Test
  void isIncrementalIsFalseAndRunQueryFails() {
    assertThrows(
        HopException.class,
        () ->
            BvSqlJinjaSupport.renderSnippet(
                "{% if is_incremental() %}X{% else %}{{ run_query('select 1') }}{% endif %}",
                List.of(), Map.of(), new Variables()));
  }

  @Test
  void sandboxBlocksJavaClassAccess() {
    HopException error =
        assertThrows(
            HopException.class,
            () ->
                BvSqlJinjaSupport.renderSnippet(
                    "SELECT '{{ ''.class }}'", List.of(), Map.of(), new Variables()));
    assertTrue(
        error.getMessage() != null
            && (error.getMessage().toLowerCase().contains("class")
                || error.getMessage().toLowerCase().contains("render")
                || error.getMessage().toLowerCase().contains("restricted")));
  }

  @Test
  void commentsAreStripped() throws Exception {
    String rendered =
        BvSqlJinjaSupport.renderSnippet(
            "SELECT 1 {# secret #}", List.of(), Map.of(), new Variables());
    assertTrue(rendered.contains("SELECT 1"));
    assertFalse(rendered.contains("secret"));
  }

  @Test
  void twoArgRefIsRecorded() throws Exception {
    BvBusinessTable table = new BvBusinessTable();
    table.setName("v1");
    table.setTableName("v1");
    table.setSqlQuery("SELECT * FROM {{ ref('vault1', 'sat_customer') }}");

    BvSqlJinjaRenderResult result =
        BvSqlJinjaSupport.render(
            table, new BusinessVaultModel(), new DataVaultModel(), null, new Variables(), null);
    List<BvSqlRef> refs = result.refs();
    assertEquals(1, refs.size());
    assertEquals("vault1", refs.get(0).getModelName());
    assertEquals("sat_customer", refs.get(0).getObjectName());
  }
}
