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
package org.hopper.edw.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValidationFindingFormatterTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void baselineMissingFindingNamesAxisAndVersion() {
    String text =
        ValidationFindingFormatter.baselineContractMissing(
            "hop/retail-example/sources/all-customer-info", "v1.0.0", true);
    assertTrue(text.contains("Axis:"), text);
    assertTrue(text.contains("Compared:"), text);
    assertTrue(text.contains("Found:"), text);
    assertTrue(text.contains("all-customer-info"), text);
    assertTrue(text.contains("v1.0.0"), text);
    assertTrue(text.toLowerCase().contains("baseline"), text);
    assertFalse(text.toLowerCase().contains("unsupported physical"), text);
  }

  @Test
  void targetDdlFindingIsTargetAxisNotSource() {
    String text =
        ValidationFindingFormatter.targetDdlRequired(
            "sat_customer", "retail-360", 2, "CREATE TABLE ...");
    assertTrue(text.contains("Target database") || text.toLowerCase().contains("target"), text);
    assertTrue(text.contains("sat_customer"), text);
    assertTrue(text.contains("Found:"), text);
    assertTrue(
        text.toLowerCase().contains("first load") || text.toLowerCase().contains("ddl"), text);
  }

  @Test
  void shortTitleExtractsFoundLine() {
    String structured =
        ValidationFindingFormatter.baselineContractMissing("hop/x/y", "v1.0.0", true);
    String shortTitle = ValidationFindingFormatter.shortTitle(structured);
    assertTrue(shortTitle.contains("hop/x/y") || shortTitle.contains("v1.0.0"), shortTitle);
    assertFalse(shortTitle.startsWith("Axis:"), shortTitle);
  }

  @Test
  void compareContextMentionsWorkingCatalogWhenNoTag() {
    String line =
        ValidationFindingFormatter.describeCompareContext(
            SchemaCompareMode.LIVE_SOURCE, null, null);
    assertTrue(
        line.toLowerCase().contains("working catalog") || line.contains("LIVE_SOURCE"), line);
  }

  @Test
  void parseStructuredExtractsFoundAndWhy() {
    String text =
        ValidationFindingFormatter.baselineContractMissing(
            "hop/retail-example/sources/all-customer-info", "v1.0.0", true);
    ValidationFindingFormatter.StructuredFinding parsed =
        ValidationFindingFormatter.parseStructured(text);
    assertTrue(parsed.found().contains("all-customer-info"), parsed.found());
    assertTrue(parsed.why().toLowerCase().contains("baseline"), parsed.why());
    assertFalse(parsed.found().startsWith("Axis:"), parsed.found());
  }

  @Test
  void humanTitleForBaselineMissing() {
    String title =
        ValidationFindingFormatter.humanTitle(ValidationReport.IssueKind.BASELINE_CONTRACT_MISSING);
    assertTrue(
        title.toLowerCase().contains("baseline") || title.toLowerCase().contains("missing"), title);
    assertFalse(title.contains("BASELINE_CONTRACT"), title);
  }

  @Test
  void summaryHelpersAvoidAxisJargon() {
    String what =
        ValidationFindingFormatter.describeWhatWeCompared(
            SchemaCompareMode.WORKING_VS_VERSION, "v1.0.0", null);
    assertTrue(what.contains("v1.0.0"), what);
    assertFalse(what.toLowerCase().startsWith("axis"), what);
    String expected =
        ValidationFindingFormatter.describeExpectedSide(
            SchemaCompareMode.WORKING_VS_VERSION, "v1.0.0", null);
    assertTrue(expected.contains("v1.0.0"), expected);
    String actual =
        ValidationFindingFormatter.describeActualSide(
            SchemaCompareMode.WORKING_VS_VERSION, "v1.0.0", null);
    assertTrue(actual.toLowerCase().contains("working"), actual);
  }
}
